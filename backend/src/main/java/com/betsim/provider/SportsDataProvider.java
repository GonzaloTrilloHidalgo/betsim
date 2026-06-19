package com.betsim.provider;

import com.betsim.provider.ProviderDtos.ProviderMatch;
import com.betsim.provider.ProviderDtos.ProviderResult;

import java.util.List;
import java.util.Optional;

/**
 * Fuente de datos deportivos. Une el rol de OddsProvider (cuotas) y ResultsProvider (resultados)
 * descritos en ARQUITECTURA_Y_PLAN.md. En el MVP basta con esta interfaz; la implementación mock
 * permite desarrollar sin coste y la real (The Odds API / API-Football) se añade sin tocar la lógica.
 */
public interface SportsDataProvider {

    /** Partidos próximos con sus cuotas 1X2 vigentes. */
    List<ProviderMatch> fetchUpcomingMatches();

    /** Resultado a 90 min de un partido, si ya ha finalizado. Vacío si sigue por jugarse/en juego. */
    Optional<ProviderResult> fetchResult(String externalId);
}
