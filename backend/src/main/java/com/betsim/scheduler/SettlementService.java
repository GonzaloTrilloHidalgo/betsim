package com.betsim.scheduler;

import com.betsim.domain.*;
import com.betsim.domain.Enums.*;
import com.betsim.provider.ProviderDtos.ProviderResult;
import com.betsim.provider.SportsDataProvider;
import com.betsim.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final SportsDataProvider provider;
    private final PartidoRepository partidos;
    private final MercadoRepository mercados;
    private final ApuestaRepository apuestas;
    private final UsuarioRepository usuarios;
    private final TransaccionRepository transacciones;

    public SettlementService(SportsDataProvider provider, PartidoRepository partidos, MercadoRepository mercados,
                             ApuestaRepository apuestas, UsuarioRepository usuarios, TransaccionRepository transacciones) {
        this.provider = provider;
        this.partidos = partidos;
        this.mercados = mercados;
        this.apuestas = apuestas;
        this.usuarios = usuarios;
        this.transacciones = transacciones;
    }

    /** Cierra mercados de partidos ya empezados y liquida los que han finalizado. */
    @Transactional
    public void run() {
        Instant ahora = Instant.now();

        // 1) Partidos que han arrancado -> EN_JUEGO y mercados CERRADOS (no se aceptan más apuestas).
        for (Partido p : partidos.findReadyToSettle(EstadoPartido.PROGRAMADO, ahora)) {
            p.setEstado(EstadoPartido.EN_JUEGO);
            cerrarMercados(p, EstadoMercado.CERRADO);
        }

        // 2) Partidos en juego con resultado disponible -> finalizar + liquidar.
        for (Partido p : List.copyOf(partidos.findByEstadoOrderByInicioUtcAsc(EstadoPartido.EN_JUEGO))) {
            Optional<ProviderResult> res = provider.fetchResult(p.getExternalId());
            if (res.isEmpty()) continue;
            liquidarPartido(p, res.get());
        }
    }

    private void liquidarPartido(Partido p, ProviderResult res) {
        p.setGolesLocal(res.golesLocal());
        p.setGolesVisitante(res.golesVisitante());
        p.setEstado(EstadoPartido.FINALIZADO);

        // Resolvemos cada selección de ESTE partido (acertada/fallada), aunque la combinada siga
        // pendiente o ya esté perdida, para que se vea su color. Solo recalculamos las que siguen vivas.
        for (Apuesta a : apuestas.findWithSelectionResultByPartido(p.getId(), ResultadoSeleccion.PENDIENTE)) {
            for (Seleccion s : a.getSelecciones()) {
                if (s.getResultado() != ResultadoSeleccion.PENDIENTE) continue;
                Partido sp = s.getOpcionCuota().getMercado().getPartido();
                if (!sp.getId().equals(p.getId())) continue;
                boolean acierto = acierta(s.getOpcionCuota(), res.golesLocal(), res.golesVisitante());
                s.setResultado(acierto ? ResultadoSeleccion.ACERTADA : ResultadoSeleccion.FALLADA);
            }
            if (a.getEstado() == EstadoApuesta.PENDIENTE) recalcularApuesta(a);
        }

        cerrarMercados(p, EstadoMercado.LIQUIDADO);
        p.setEstado(EstadoPartido.LIQUIDADO);
        log.info("Partido {} liquidado: {} {}-{} {}", p.getExternalId(), p.getEquipoLocal(),
                res.golesLocal(), res.golesVisitante(), p.getEquipoVisitante());
    }

    /** Determina si una selección acierta, según el tipo de su mercado y el marcador a 90'. */
    private boolean acierta(OpcionCuota oc, int gl, int gv) {
        TipoMercado tipo = oc.getMercado().getTipo();
        String codigo = oc.getCodigo();
        Resultado1x2 real = gl > gv ? Resultado1x2.LOCAL : gl == gv ? Resultado1x2.EMPATE : Resultado1x2.VISITANTE;
        int total = gl + gv;
        return switch (tipo) {
            case UNO_X_DOS -> codigo.equals(real.name());
            case DOBLE_OPORTUNIDAD -> switch (codigo) {
                case "1X" -> real != Resultado1x2.VISITANTE;
                case "12" -> real != Resultado1x2.EMPATE;
                case "X2" -> real != Resultado1x2.LOCAL;
                default -> false;
            };
            case OVER_UNDER -> {
                double linea = oc.getLinea() == null ? 2.5 : oc.getLinea().doubleValue();
                yield codigo.startsWith("OVER") ? total > linea : total < linea;
            }
            case AMBOS_MARCAN -> {
                boolean ambos = gl > 0 && gv > 0;
                yield "BTTS_SI".equals(codigo) ? ambos : !ambos;
            }
            case GOLEADOR -> false; // se implementará en la v1.1 con datos de goleadores
        };
    }

    private void recalcularApuesta(Apuesta a) {
        boolean algunaFallada = a.getSelecciones().stream()
                .anyMatch(s -> s.getResultado() == ResultadoSeleccion.FALLADA);
        boolean algunaPendiente = a.getSelecciones().stream()
                .anyMatch(s -> s.getResultado() == ResultadoSeleccion.PENDIENTE);

        if (algunaFallada) {
            a.setEstado(EstadoApuesta.PERDIDA);
            a.setResueltoEn(Instant.now());
        } else if (!algunaPendiente) {
            // Todas las selecciones ACERTADAS (o ANULADAS) -> apuesta ganada.
            a.setEstado(EstadoApuesta.GANADA);
            a.setResueltoEn(Instant.now());
            Usuario u = a.getUsuario();
            u.setSaldo(u.getSaldo().add(a.getRetornoPotencial()));
            usuarios.save(u);
            transacciones.save(new Transaccion(u, a, TipoTransaccion.PREMIO, a.getRetornoPotencial(), u.getSaldo()));
        }
        // si queda alguna pendiente (otros partidos sin terminar) -> sigue PENDIENTE
    }

    private void cerrarMercados(Partido p, EstadoMercado estado) {
        for (Mercado m : mercados.findByPartidoId(p.getId())) {
            m.setEstado(estado);
        }
    }
}
