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

        Resultado1x2 real = res.golesLocal() > res.golesVisitante() ? Resultado1x2.LOCAL
                : res.golesLocal() == res.golesVisitante() ? Resultado1x2.EMPATE
                : Resultado1x2.VISITANTE;

        for (Apuesta a : apuestas.findPendingByPartido(p.getId(), EstadoApuesta.PENDIENTE)) {
            // Resolver las selecciones de ESTE partido.
            for (Seleccion s : a.getSelecciones()) {
                if (s.getResultado() != ResultadoSeleccion.PENDIENTE) continue;
                Partido sp = s.getOpcionCuota().getMercado().getPartido();
                if (!sp.getId().equals(p.getId())) continue;
                boolean acierto = s.getOpcionCuota().getCodigo().equals(real.name());
                s.setResultado(acierto ? ResultadoSeleccion.ACERTADA : ResultadoSeleccion.FALLADA);
            }
            recalcularApuesta(a);
        }

        cerrarMercados(p, EstadoMercado.LIQUIDADO);
        p.setEstado(EstadoPartido.LIQUIDADO);
        log.info("Partido {} liquidado: {} {}-{} {}", p.getExternalId(), p.getEquipoLocal(),
                res.golesLocal(), res.golesVisitante(), p.getEquipoVisitante());
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
