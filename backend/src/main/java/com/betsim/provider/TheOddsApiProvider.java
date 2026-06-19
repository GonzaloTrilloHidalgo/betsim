package com.betsim.provider;

import com.betsim.domain.Enums.Fase;
import com.betsim.provider.ProviderDtos.ProviderMatch;
import com.betsim.provider.ProviderDtos.ProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementación real contra The Odds API (mercado h2h = 1X2). Se activa con
 * betsim.provider.type=theoddsapi y ODDS_API_KEY definido. Solo cuotas y marcador:
 * el mercado de goleadores (player props) llegará en la v1.1 con su proveedor de resultados.
 */
@Component
@ConditionalOnProperty(name = "betsim.provider.type", havingValue = "theoddsapi")
public class TheOddsApiProvider implements SportsDataProvider {

    private static final Logger log = LoggerFactory.getLogger(TheOddsApiProvider.class);
    private static final String BASE = "https://api.the-odds-api.com/v4";

    private final RestClient http = RestClient.create();
    private final String sportKey;
    private final String apiKey;

    public TheOddsApiProvider(@Value("${betsim.provider.sport-key}") String sportKey,
                              @Value("${betsim.provider.odds-api-key}") String apiKey) {
        this.sportKey = sportKey;
        this.apiKey = apiKey;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ODDS_API_KEY no definido: las llamadas a The Odds API fallarán.");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProviderMatch> fetchUpcomingMatches() {
        String url = BASE + "/sports/" + sportKey + "/odds?regions=eu&markets=h2h&oddsFormat=decimal&apiKey=" + apiKey;
        List<Map<String, Object>> events = http.get().uri(url).retrieve().body(List.class);
        List<ProviderMatch> out = new ArrayList<>();
        if (events == null) return out;
        for (Map<String, Object> ev : events) {
            String home = (String) ev.get("home_team");
            String away = (String) ev.get("away_team");
            Instant kickoff = Instant.parse((String) ev.get("commence_time"));
            BigDecimal cl = null, ce = null, cv = null;
            List<Map<String, Object>> books = (List<Map<String, Object>>) ev.getOrDefault("bookmakers", List.of());
            if (!books.isEmpty()) {
                List<Map<String, Object>> markets = (List<Map<String, Object>>) books.get(0).getOrDefault("markets", List.of());
                for (Map<String, Object> mk : markets) {
                    if (!"h2h".equals(mk.get("key"))) continue;
                    for (Map<String, Object> oc : (List<Map<String, Object>>) mk.getOrDefault("outcomes", List.of())) {
                        String name = (String) oc.get("name");
                        BigDecimal price = new BigDecimal(String.valueOf(oc.get("price")));
                        if (name.equalsIgnoreCase(home)) cl = price;
                        else if (name.equalsIgnoreCase(away)) cv = price;
                        else ce = price; // "Draw"
                    }
                }
            }
            if (cl == null || ce == null || cv == null) continue; // sin cuotas completas, lo ignoramos
            out.add(new ProviderMatch((String) ev.get("id"), home, away, kickoff, Fase.GRUPOS, cl, ce, cv));
        }
        return out;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<ProviderResult> fetchResult(String externalId) {
        String url = BASE + "/sports/" + sportKey + "/scores?daysFrom=3&apiKey=" + apiKey;
        List<Map<String, Object>> events = http.get().uri(url).retrieve().body(List.class);
        if (events == null) return Optional.empty();
        for (Map<String, Object> ev : events) {
            if (!externalId.equals(ev.get("id"))) continue;
            if (!Boolean.TRUE.equals(ev.get("completed"))) return Optional.empty();
            String home = (String) ev.get("home_team");
            Integer gl = null, gv = null;
            for (Map<String, Object> sc : (List<Map<String, Object>>) ev.getOrDefault("scores", List.of())) {
                int goals = Integer.parseInt(String.valueOf(sc.get("score")));
                if (((String) sc.get("name")).equalsIgnoreCase(home)) gl = goals; else gv = goals;
            }
            if (gl != null && gv != null) return Optional.of(new ProviderResult(externalId, gl, gv));
        }
        return Optional.empty();
    }
}
