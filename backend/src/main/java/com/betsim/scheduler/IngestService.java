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

    /** Sincroniza calendario + cuotas 1X2: upsert idempotente de partido/mercado/opciones. */
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

            // Mercado 1X2 (creado una vez por partido).
            Mercado mercado = mercados.findByPartidoIdAndTipo(p.getId(), TipoMercado.UNO_X_DOS)
                    .orElseGet(() -> mercados.save(new Mercado(p, TipoMercado.UNO_X_DOS)));

            upsertOpcion(mercado, "LOCAL", p.getEquipoLocal(), pm.cuotaLocal(), pm.casaLocal());
            upsertOpcion(mercado, "EMPATE", "Empate", pm.cuotaEmpate(), pm.casaEmpate());
            upsertOpcion(mercado, "VISITANTE", p.getEquipoVisitante(), pm.cuotaVisitante(), pm.casaVisitante());
            procesados++;
        }
        log.info("Ingesta completada: {} partidos sincronizados", procesados);
        return procesados;
    }

    private void upsertOpcion(Mercado mercado, String codigo, String descripcion, BigDecimal cuota, String casa) {
        OpcionCuota oc = opciones.findByMercadoIdAndCodigo(mercado.getId(), codigo).orElseGet(() -> {
            OpcionCuota nueva = new OpcionCuota();
            nueva.setMercado(mercado);
            nueva.setCodigo(codigo);
            return nueva;
        });
        oc.setDescripcion(descripcion);
        oc.setCuota(cuota);
        oc.setCasa(casa);
        oc.setDisponible(true);
        oc.setActualizadoEn(Instant.now());
        opciones.save(oc);
    }
}
