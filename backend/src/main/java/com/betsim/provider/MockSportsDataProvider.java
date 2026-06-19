package com.betsim.provider;

import com.betsim.domain.Enums.Fase;
import com.betsim.provider.ProviderDtos.OverUnder;
import com.betsim.provider.ProviderDtos.ProviderMatch;
import com.betsim.provider.ProviderDtos.ProviderResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Proveedor simulado: genera un cuadro de partidos del Mundial alrededor del arranque, con cuotas
 * coherentes y resultados deterministas (derivados del external_id) para que la liquidación sea estable.
 * Permite jugar y probar todo el flujo sin gastar créditos de API ni necesitar clave.
 */
@Component
@ConditionalOnProperty(name = "betsim.provider.type", havingValue = "mock", matchIfMissing = true)
public class MockSportsDataProvider implements SportsDataProvider {

    private static final String[][] DUELOS = {
            {"España", "Alemania"}, {"Argentina", "Brasil"}, {"Francia", "Inglaterra"},
            {"Portugal", "Países Bajos"}, {"Italia", "Croacia"}, {"México", "Estados Unidos"},
            {"Bélgica", "Uruguay"}, {"Marruecos", "Senegal"}, {"Japón", "Corea del Sur"},
            {"Colombia", "Ecuador"}
    };

    // Desfase de cada partido respecto al arranque (en horas). Negativos = ya disputados.
    private static final long[] OFFSET_HORAS = {-3, -2, 1, 3, 6, 26, 30, 50, 54, 72};

    private final List<ProviderMatch> partidos = new ArrayList<>();

    public MockSportsDataProvider() {
        Instant base = Instant.now();
        for (int i = 0; i < DUELOS.length; i++) {
            String externalId = "WC2026-MOCK-" + (i + 1);
            Instant kickoff = base.plus(OFFSET_HORAS[i], ChronoUnit.HOURS);
            Random r = new Random(externalId.hashCode());
            BigDecimal cl = cuota(r, 1.5, 3.5);
            BigDecimal ce = cuota(r, 2.8, 3.8);
            BigDecimal cv = cuota(r, 1.5, 3.5);
            // Varias líneas de Over/Under con cuotas plausibles (over más barato en líneas bajas).
            double[][] rangos = {{0.5, 1.04, 1.12, 5.0, 9.0}, {1.5, 1.30, 1.55, 2.5, 3.2},
                                 {2.5, 1.85, 2.30, 1.55, 1.90}, {3.5, 3.30, 4.60, 1.18, 1.30}};
            List<OverUnder> ou = new ArrayList<>();
            for (double[] g : rangos) {
                ou.add(new OverUnder(BigDecimal.valueOf(g[0]), cuota(r, g[1], g[2]), cuota(r, g[3], g[4]),
                        "Simulada", "Simulada"));
            }
            partidos.add(new ProviderMatch(externalId, DUELOS[i][0], DUELOS[i][1], kickoff,
                    Fase.GRUPOS, cl, ce, cv, "Simulada", "Simulada", "Simulada", ou));
        }
    }

    @Override
    public List<ProviderMatch> fetchUpcomingMatches() {
        return List.copyOf(partidos);
    }

    @Override
    public Optional<ProviderResult> fetchResult(String externalId) {
        ProviderMatch m = partidos.stream()
                .filter(p -> p.externalId().equals(externalId))
                .findFirst().orElse(null);
        if (m == null) return Optional.empty();
        // Un partido se considera finalizado 105 min después del kickoff (90' + descanso).
        if (Instant.now().isBefore(m.inicioUtc().plus(105, ChronoUnit.MINUTES))) {
            return Optional.empty();
        }
        Random r = new Random(("RESULT-" + externalId).hashCode());
        return Optional.of(new ProviderResult(externalId, r.nextInt(4), r.nextInt(4)));
    }

    private static BigDecimal cuota(Random r, double min, double max) {
        double v = min + (max - min) * r.nextDouble();
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
