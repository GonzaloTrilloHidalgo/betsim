package com.betsim.api;

import com.betsim.domain.*;
import com.betsim.domain.Enums.EstadoPartido;
import com.betsim.repository.LigaRepository;
import com.betsim.repository.MercadoRepository;
import com.betsim.repository.OpcionCuotaRepository;
import com.betsim.repository.PartidoRepository;
import com.betsim.web.ApiException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class MatchController {

    private final PartidoRepository partidos;
    private final MercadoRepository mercados;
    private final OpcionCuotaRepository opciones;
    private final LigaRepository ligas;

    public MatchController(PartidoRepository partidos, MercadoRepository mercados,
                          OpcionCuotaRepository opciones, LigaRepository ligas) {
        this.partidos = partidos;
        this.mercados = mercados;
        this.opciones = opciones;
        this.ligas = ligas;
    }

    public record OptionView(Long id, String codigo, String descripcion, BigDecimal cuota, boolean disponible, String casa, BigDecimal linea) {}
    public record MarketView(Long id, String tipo, String estado, List<OptionView> opciones) {}
    public record MatchView(Long id, String equipoLocal, String equipoVisitante, Instant inicioUtc,
                            String fase, String estado, Integer golesLocal, Integer golesVisitante,
                            List<MarketView> mercados) {}
    public record LeagueView(Long id, String nombre, String pais) {}
    public record ResultView(Long id, String equipoLocal, String equipoVisitante, Instant inicioUtc,
                             String fase, Integer golesLocal, Integer golesVisitante) {}

    @GetMapping("/matches")
    public List<MatchView> upcoming() {
        return partidos.findByEstadoOrderByInicioUtcAsc(EstadoPartido.PROGRAMADO).stream()
                .map(this::toView).toList();
    }

    /** Partidos ya jugados con su marcador (más recientes primero). */
    @GetMapping("/matches/results")
    public List<ResultView> results() {
        return partidos.findByEstadoInOrderByInicioUtcDesc(
                        List.of(EstadoPartido.FINALIZADO, EstadoPartido.LIQUIDADO)).stream()
                .limit(60)
                .map(p -> new ResultView(p.getId(), p.getEquipoLocal(), p.getEquipoVisitante(),
                        p.getInicioUtc(), p.getFase() == null ? null : p.getFase().name(),
                        p.getGolesLocal(), p.getGolesVisitante()))
                .toList();
    }

    @GetMapping("/matches/{id}")
    public MatchView one(@PathVariable Long id) {
        Partido p = partidos.findById(id).orElseThrow(() -> ApiException.notFound("Partido no encontrado"));
        return toView(p);
    }

    @GetMapping("/leagues")
    public List<LeagueView> leagues() {
        return ligas.findByActivaTrue().stream()
                .map(l -> new LeagueView(l.getId(), l.getNombre(), l.getPais())).toList();
    }

    private MatchView toView(Partido p) {
        List<MarketView> markets = mercados.findByPartidoId(p.getId()).stream().map(m -> {
            List<OptionView> opts = opciones.findByMercadoId(m.getId()).stream()
                    .map(o -> new OptionView(o.getId(), o.getCodigo(), o.getDescripcion(), o.getCuota(), o.isDisponible(), o.getCasa(), o.getLinea()))
                    .toList();
            return new MarketView(m.getId(), m.getTipo().name(), m.getEstado().name(), opts);
        }).toList();
        return new MatchView(p.getId(), p.getEquipoLocal(), p.getEquipoVisitante(), p.getInicioUtc(),
                p.getFase() == null ? null : p.getFase().name(), p.getEstado().name(),
                p.getGolesLocal(), p.getGolesVisitante(), markets);
    }
}
