package com.betsim.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cliente de football-data.org (v4): trae los partidos del Mundial con grupos, fases y marcadores.
 * Se usa solo para MOSTRAR resultados y el cuadro eliminatorio (las apuestas siguen con The Odds API).
 * Cachea la respuesta para respetar el límite del plan gratuito (10 peticiones/min).
 */
@Component
public class FootballDataClient {

    private static final Logger log = LoggerFactory.getLogger(FootballDataClient.class);
    private static final String BASE = "https://api.football-data.org/v4";

    private final RestClient http = RestClient.create();
    private final String apiKey;
    private final String competition;
    private final Duration cacheTtl;

    private List<FdMatch> cache = List.of();
    private Instant cacheAt = Instant.EPOCH;

    public FootballDataClient(@Value("${betsim.football-data.api-key:}") String apiKey,
                              @Value("${betsim.football-data.competition:WC}") String competition,
                              @Value("${betsim.football-data.cache:PT10M}") Duration cacheTtl) {
        this.apiKey = apiKey;
        this.competition = competition;
        this.cacheTtl = cacheTtl;
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Partido del Mundial tal como lo necesita la app (resultados y cuadro). */
    public record FdMatch(
            long id, Instant utcDate, String status, String stage, String group,
            String homeName, String awayName, String homeCrest, String awayCrest,
            Integer homeGoals, Integer awayGoals, String winner) {

        public boolean finished() { return "FINISHED".equals(status); }
        public boolean live() { return "IN_PLAY".equals(status) || "PAUSED".equals(status); }
        public boolean knockout() { return stage != null && !"GROUP_STAGE".equals(stage); }
    }

    @SuppressWarnings("unchecked")
    public synchronized List<FdMatch> matches() {
        if (!enabled()) return List.of();
        if (Instant.now().isBefore(cacheAt.plus(cacheTtl))) return cache;

        String url = BASE + "/competitions/" + competition + "/matches";
        try {
            Map<String, Object> body = http.get().uri(url)
                    .header("X-Auth-Token", apiKey)
                    .retrieve().body(Map.class);
            List<Map<String, Object>> raw = body == null ? List.of()
                    : (List<Map<String, Object>>) body.getOrDefault("matches", List.of());
            List<FdMatch> out = new ArrayList<>(raw.size());
            for (Map<String, Object> m : raw) out.add(parse(m));
            cache = out;
            cacheAt = Instant.now();
            log.info("football-data.org: {} partidos del Mundial obtenidos.", out.size());
        } catch (Exception e) {
            log.error("Error consultando football-data.org: {}", e.getMessage());
            // Conserva la caché anterior para no romper la vista.
        }
        return cache;
    }

    @SuppressWarnings("unchecked")
    private FdMatch parse(Map<String, Object> m) {
        Map<String, Object> home = (Map<String, Object>) m.getOrDefault("homeTeam", Map.of());
        Map<String, Object> away = (Map<String, Object>) m.getOrDefault("awayTeam", Map.of());
        Map<String, Object> score = (Map<String, Object>) m.getOrDefault("score", Map.of());
        Map<String, Object> ft = (Map<String, Object>) score.getOrDefault("fullTime", Map.of());
        return new FdMatch(
                ((Number) m.getOrDefault("id", 0)).longValue(),
                m.get("utcDate") != null ? Instant.parse((String) m.get("utcDate")) : null,
                (String) m.get("status"),
                (String) m.get("stage"),
                (String) m.get("group"),
                (String) home.get("name"), (String) away.get("name"),
                (String) home.get("crest"), (String) away.get("crest"),
                ft.get("home") == null ? null : ((Number) ft.get("home")).intValue(),
                ft.get("away") == null ? null : ((Number) ft.get("away")).intValue(),
                (String) score.get("winner"));
    }
}
