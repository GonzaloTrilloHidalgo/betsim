package com.betsim.api;

import com.betsim.domain.Enums.EstadoPartido;
import com.betsim.domain.Partido;
import com.betsim.provider.FootballDataClient;
import com.betsim.provider.FootballDataClient.FdMatch;
import com.betsim.repository.PartidoRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultados completos y cuadro eliminatorio del Mundial (datos de football-data.org).
 * Si no hay clave de football-data, /results cae a los partidos liquidados de nuestra BD.
 */
@RestController
@RequestMapping("/api/v1")
public class TournamentController {

    // Orden de las fases en el cuadro (de izquierda a derecha).
    private static final List<String> ORDEN_FASES = List.of(
            "LAST_32", "LAST_16", "QUARTER_FINALS", "SEMI_FINALS", "THIRD_PLACE", "FINAL");

    private final FootballDataClient footballData;
    private final PartidoRepository partidos;

    public TournamentController(FootballDataClient footballData, PartidoRepository partidos) {
        this.footballData = footballData;
        this.partidos = partidos;
    }

    public record MatchResult(String fase, String grupo, Instant fecha,
                              String local, String visitante, String escudoLocal, String escudoVisitante,
                              Integer golesLocal, Integer golesVisitante, String estado, String ganador) {}

    public record BracketMatch(String local, String visitante, String escudoLocal, String escudoVisitante,
                               Integer golesLocal, Integer golesVisitante, String estado, String ganador) {}
    public record BracketStage(String fase, List<BracketMatch> partidos) {}

    /** Partidos en juego ahora mismo, con su marcador en directo (football-data). */
    @GetMapping("/live")
    public List<MatchResult> live() {
        if (!footballData.enabled()) return List.of();
        return footballData.matches().stream()
                .filter(FdMatch::live)
                .sorted(Comparator.comparing(FdMatch::utcDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(m -> new MatchResult(m.stage(), m.group(), m.utcDate(),
                        m.homeName(), m.awayName(), m.homeCrest(), m.awayCrest(),
                        m.homeGoals(), m.awayGoals(), m.status(), m.winner()))
                .toList();
    }

    /** Todos los partidos ya jugados (football-data) o, en su defecto, los de nuestra BD. */
    @GetMapping("/results")
    public List<MatchResult> results() {
        if (footballData.enabled()) {
            return footballData.matches().stream()
                    .filter(FdMatch::finished)
                    .sorted(Comparator.comparing(FdMatch::utcDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .map(m -> new MatchResult(m.stage(), m.group(), m.utcDate(),
                            m.homeName(), m.awayName(), m.homeCrest(), m.awayCrest(),
                            m.homeGoals(), m.awayGoals(), m.status(), m.winner()))
                    .toList();
        }
        // Fallback: resultados de nuestra BD (lo ya jugado y liquidado por The Odds API).
        return partidos.findByEstadoInOrderByInicioUtcDesc(
                        List.of(EstadoPartido.FINALIZADO, EstadoPartido.LIQUIDADO)).stream()
                .map(p -> new MatchResult(p.getFase() == null ? null : p.getFase().name(), null, p.getInicioUtc(),
                        p.getEquipoLocal(), p.getEquipoVisitante(), null, null,
                        p.getGolesLocal(), p.getGolesVisitante(), "FINISHED", null))
                .toList();
    }

    /** Cuadro eliminatorio agrupado por fase (vacío si aún no ha empezado o sin clave). */
    @GetMapping("/bracket")
    public List<BracketStage> bracket() {
        // Prepara las columnas en orden, aunque estén vacías.
        Map<String, List<BracketMatch>> porFase = new LinkedHashMap<>();
        for (String f : ORDEN_FASES) porFase.put(f, new ArrayList<>());

        if (footballData.enabled()) {
            footballData.matches().stream()
                    .filter(FdMatch::knockout)
                    .sorted(Comparator.comparing(FdMatch::utcDate, Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(m -> porFase.computeIfAbsent(m.stage(), k -> new ArrayList<>())
                            .add(new BracketMatch(m.homeName(), m.awayName(), m.homeCrest(), m.awayCrest(),
                                    m.homeGoals(), m.awayGoals(), m.status(), m.winner())));
        }

        List<BracketStage> out = new ArrayList<>();
        porFase.forEach((fase, lista) -> out.add(new BracketStage(fase, lista)));
        // Solo devolvemos fases conocidas con orden; las desconocidas (si las hubiera) van al final.
        out.sort(Comparator.comparingInt(s -> {
            int i = ORDEN_FASES.indexOf(s.fase());
            return i < 0 ? Integer.MAX_VALUE : i;
        }));
        return out;
    }
}
