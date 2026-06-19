package com.betsim.provider;

import com.betsim.provider.ProviderDtos.ProviderMatch;
import com.betsim.provider.ProviderDtos.ProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementación real contra The Odds API (mercado h2h = 1X2). Se activa con
 * betsim.provider.type=theoddsapi y ODDS_API_KEY definido. Solo cuotas y marcador:
 * el mercado de goleadores (player props) llegará en la v1.1 con su proveedor de resultados.
 *
 * Frugalidad con el free tier: el endpoint /scores (cuesta 2 créditos por usar daysFrom) se cachea
 * durante {@code results-cache} para que una ronda de liquidación con N partidos no gaste N llamadas.
 */
@Component
@ConditionalOnProperty(name = "betsim.provider.type", havingValue = "theoddsapi")
public class TheOddsApiProvider implements SportsDataProvider {

    private static final Logger log = LoggerFactory.getLogger(TheOddsApiProvider.class);
    private static final String BASE = "https://api.the-odds-api.com/v4";

    private final RestClient http = RestClient.create();
    private final String sportKey;
    private final String apiKey;
    private final String region;
    private final Duration resultsCacheTtl;

    // Caché simple del feed de resultados para no gastar créditos por cada partido consultado.
    private List<Map<String, Object>> scoresCache = List.of();
    private Instant scoresCacheAt = Instant.EPOCH;

    public TheOddsApiProvider(@Value("${betsim.provider.sport-key}") String sportKey,
                              @Value("${betsim.provider.odds-api-key}") String apiKey,
                              @Value("${betsim.provider.region:eu}") String region,
                              @Value("${betsim.provider.results-cache:PT15M}") Duration resultsCacheTtl) {
        this.sportKey = sportKey;
        this.apiKey = apiKey;
        this.region = region;
        this.resultsCacheTtl = resultsCacheTtl;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ODDS_API_KEY no definido: las llamadas a The Odds API fallarán. Define la variable ODDS_API_KEY.");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProviderMatch> fetchUpcomingMatches() {
        String url = BASE + "/sports/" + sportKey + "/odds?regions=" + region
                + "&markets=h2h&oddsFormat=decimal&apiKey=" + apiKey;
        List<Map<String, Object>> events;
        try {
            events = http.get().uri(url).retrieve().body(List.class);
        } catch (Exception e) {
            log.error("Error consultando cuotas en The Odds API (sportKey={}): {}", sportKey, e.getMessage());
            return List.of();
        }
        if (events == null || events.isEmpty()) {
            log.warn("The Odds API no devolvió partidos para sportKey='{}'. ¿Es correcta la clave del deporte "
                    + "y hay partidos próximos con cuotas?", sportKey);
            return List.of();
        }

        List<ProviderMatch> out = new ArrayList<>();
        for (Map<String, Object> ev : events) {
            String home = (String) ev.get("home_team");
            String away = (String) ev.get("away_team");
            Instant kickoff = Instant.parse((String) ev.get("commence_time"));
            // Mejor cuota disponible (la más alta entre todas las casas) para cada resultado.
            BigDecimal cl = null, ce = null, cv = null;
            List<Map<String, Object>> books = (List<Map<String, Object>>) ev.getOrDefault("bookmakers", List.of());
            for (Map<String, Object> book : books) {
                List<Map<String, Object>> markets = (List<Map<String, Object>>) book.getOrDefault("markets", List.of());
                for (Map<String, Object> mk : markets) {
                    if (!"h2h".equals(mk.get("key"))) continue;
                    for (Map<String, Object> oc : (List<Map<String, Object>>) mk.getOrDefault("outcomes", List.of())) {
                        String name = (String) oc.get("name");
                        BigDecimal price = new BigDecimal(String.valueOf(oc.get("price")));
                        if (name.equalsIgnoreCase(home)) cl = max(cl, price);
                        else if (name.equalsIgnoreCase(away)) cv = max(cv, price);
                        else ce = max(ce, price); // "Draw"
                    }
                }
            }
            if (cl == null || ce == null || cv == null) continue; // sin cuotas 1X2 completas, lo ignoramos
            // The Odds API no aporta la fase del torneo -> la dejamos sin etiqueta (null).
            out.add(new ProviderMatch((String) ev.get("id"), home, away, kickoff, null, cl, ce, cv));
        }
        log.info("The Odds API: {} partidos con cuotas 1X2 obtenidos (mejor cuota disponible).", out.size());
        return out;
    }

    private static BigDecimal max(BigDecimal current, BigDecimal candidate) {
        return (current == null || candidate.compareTo(current) > 0) ? candidate : current;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<ProviderResult> fetchResult(String externalId) {
        for (Map<String, Object> ev : getScores()) {
            if (!externalId.equals(ev.get("id"))) continue;
            if (!Boolean.TRUE.equals(ev.get("completed"))) return Optional.empty();
            String home = (String) ev.get("home_team");
            Integer gl = null, gv = null;
            for (Map<String, Object> sc : (List<Map<String, Object>>) ev.getOrDefault("scores", List.of())) {
                if (sc.get("score") == null) continue;
                int goals = Integer.parseInt(String.valueOf(sc.get("score")));
                if (((String) sc.get("name")).equalsIgnoreCase(home)) gl = goals; else gv = goals;
            }
            if (gl != null && gv != null) return Optional.of(new ProviderResult(externalId, gl, gv));
        }
        return Optional.empty();
    }

    /** Devuelve el feed de resultados, usando caché para minimizar el consumo de créditos. */
    @SuppressWarnings("unchecked")
    private synchronized List<Map<String, Object>> getScores() {
        if (Instant.now().isBefore(scoresCacheAt.plus(resultsCacheTtl))) {
            return scoresCache;
        }
        String url = BASE + "/sports/" + sportKey + "/scores?daysFrom=3&apiKey=" + apiKey;
        try {
            List<Map<String, Object>> events = http.get().uri(url).retrieve().body(List.class);
            scoresCache = events == null ? List.of() : events;
            scoresCacheAt = Instant.now();
        } catch (Exception e) {
            log.error("Error consultando resultados en The Odds API: {}", e.getMessage());
            // Mantiene la caché anterior (posiblemente vacía) para no romper la liquidación.
        }
        return scoresCache;
    }
}
