package com.betsim.scheduler;

import com.betsim.domain.*;
import com.betsim.domain.Enums.TipoMercado;
import com.betsim.provider.ProviderDtos.ProviderMatch;
import com.betsim.provider.SportsDataProvider;
import com.betsim.repository.*;
import com.betsim.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final SportsDataProvider provider;
    private final LigaRepository ligas;
    private final PartidoRepository partidos;
    private final MercadoRepository mercados;
    private final OpcionCuotaRepository opciones;
    private final String sportKey;

    public IngestService(SportsDataProvider provider, LigaRepository ligas, PartidoRepository partidos,
                         MercadoRepository mercados, OpcionCuotaRepository opciones,
                         @Value("${betsim.provider.sport-key}") String sportKey) {
        this.provider = provider;
        this.ligas = ligas;
        this.partidos = partidos;
        this.mercados = mercados;
        this.opciones = opciones;
        this.sportKey = sportKey;
    }

    /** Sincroniza calendario + cuotas (1X2, Over/Under, Doble oportunidad, BTTS): upsert idempotente. */
    @Transactional
    public int sync() {
        Liga liga = ligas.findBySportKey(sportKey)
                .orElseThrow(() -> ApiException.notFound("Liga no configurada: " + sportKey));
        int procesados = 0;
        for (ProviderMatch pm : provider.fetchUpcomingMatches()) {
            Partido p = partidos.findByExternalId(pm.externalId()).orElseGet(() -> {
                Partido nuevo = new Partido();
                nuevo.setLiga(liga);
                nuevo.setExternalId(pm.externalId());
                nuevo.setEquipoLocal(pm.equipoLocal());
                nuevo.setEquipoVisitante(pm.equipoVisitante());
                nuevo.setInicioUtc(pm.inicioUtc());
                nuevo.setFase(pm.fase());
                return partidos.save(nuevo);
            });

            // 1X2 (resultado a 90').
            Mercado m1x2 = mercado(p, TipoMercado.UNO_X_DOS);
            upsertOpcion(m1x2, "LOCAL", p.getEquipoLocal(), pm.cuotaLocal(), pm.casaLocal(), null);
            upsertOpcion(m1x2, "EMPATE", "Empate", pm.cuotaEmpate(), pm.casaEmpate(), null);
            upsertOpcion(m1x2, "VISITANTE", p.getEquipoVisitante(), pm.cuotaVisitante(), pm.casaVisitante(), null);

            // Over/Under (solo si el proveedor aporta totals).
            if (pm.lineaOU() != null && pm.cuotaOver() != null && pm.cuotaUnder() != null) {
                Mercado mou = mercado(p, TipoMercado.OVER_UNDER);
                String linea = pm.lineaOU().stripTrailingZeros().toPlainString();
                upsertOpcion(mou, "OVER", "Más de " + linea + " goles", pm.cuotaOver(), pm.casaOver(), pm.lineaOU());
                upsertOpcion(mou, "UNDER", "Menos de " + linea + " goles", pm.cuotaUnder(), pm.casaUnder(), pm.lineaOU());
            }

            // Doble oportunidad (derivada del 1X2: combinación justa de dos resultados).
            Mercado mdc = mercado(p, TipoMercado.DOBLE_OPORTUNIDAD);
            upsertOpcion(mdc, "1X", p.getEquipoLocal() + " o Empate",
                    combinar(pm.cuotaLocal(), pm.cuotaEmpate()), "Derivada", null);
            upsertOpcion(mdc, "12", p.getEquipoLocal() + " o " + p.getEquipoVisitante(),
                    combinar(pm.cuotaLocal(), pm.cuotaVisitante()), "Derivada", null);
            upsertOpcion(mdc, "X2", "Empate o " + p.getEquipoVisitante(),
                    combinar(pm.cuotaEmpate(), pm.cuotaVisitante()), "Derivada", null);

            // Ambos marcan (cuotas estimadas a partir de las probabilidades implícitas del 1X2).
            Mercado mbtts = mercado(p, TipoMercado.AMBOS_MARCAN);
            BigDecimal[] btts = cuotasBtts(pm.cuotaLocal(), pm.cuotaEmpate(), pm.cuotaVisitante());
            upsertOpcion(mbtts, "BTTS_SI", "Ambos marcan: Sí", btts[0], "Estimada", null);
            upsertOpcion(mbtts, "BTTS_NO", "Ambos marcan: No", btts[1], "Estimada", null);

            procesados++;
        }
        log.info("Ingesta completada: {} partidos sincronizados", procesados);
        return procesados;
    }

    private Mercado mercado(Partido p, TipoMercado tipo) {
        return mercados.findByPartidoIdAndTipo(p.getId(), tipo)
                .orElseGet(() -> mercados.save(new Mercado(p, tipo)));
    }

    /**
     * Cuota justa de combinar dos resultados mutuamente excluyentes: 1/(1/a + 1/b) = a·b/(a+b).
     * Con un suelo de 1.01: una cuota nunca puede ser menor que 1 (las cuotas incoherentes del mock
     * podrían bajar de 1; con datos reales no ocurre).
     */
    private static BigDecimal combinar(BigDecimal a, BigDecimal b) {
        BigDecimal dc = a.multiply(b).divide(a.add(b), 2, RoundingMode.HALF_UP);
        return dc.compareTo(new BigDecimal("1.01")) < 0 ? new BigDecimal("1.01") : dc;
    }

    /**
     * Estima cuotas de "ambos marcan" Sí/No a partir del 1X2. No es exacto (lo exacto requiere player
     * props de pago), pero da valores plausibles: cuanto más igualado el partido, más probable el "Sí".
     */
    private static BigDecimal[] cuotasBtts(BigDecimal o1, BigDecimal ox, BigDecimal o2) {
        double i1 = 1.0 / o1.doubleValue(), ix = 1.0 / ox.doubleValue(), i2 = 1.0 / o2.doubleValue();
        double sum = i1 + ix + i2;
        double pHome = i1 / sum, pAway = i2 / sum;
        double igualdad = 1.0 - Math.abs(pHome - pAway);      // 0 (desigual) .. 1 (parejo)
        double pSi = Math.min(0.62, Math.max(0.40, 0.42 + 0.18 * igualdad));
        BigDecimal si = BigDecimal.valueOf(1.0 / pSi).setScale(2, RoundingMode.HALF_UP);
        BigDecimal no = BigDecimal.valueOf(1.0 / (1.0 - pSi)).setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal[]{si, no};
    }

    private void upsertOpcion(Mercado mercado, String codigo, String descripcion,
                              BigDecimal cuota, String casa, BigDecimal linea) {
        OpcionCuota oc = opciones.findByMercadoIdAndCodigo(mercado.getId(), codigo).orElseGet(() -> {
            OpcionCuota nueva = new OpcionCuota();
            nueva.setMercado(mercado);
            nueva.setCodigo(codigo);
            return nueva;
        });
        oc.setDescripcion(descripcion);
        oc.setCuota(cuota);
        oc.setCasa(casa);
        oc.setLinea(linea);
        oc.setDisponible(true);
        oc.setActualizadoEn(Instant.now());
        opciones.save(oc);
    }
}
