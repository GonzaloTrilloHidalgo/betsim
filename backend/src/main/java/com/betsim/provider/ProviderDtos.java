package com.betsim.provider;

import com.betsim.domain.Enums.Fase;

import java.math.BigDecimal;
import java.time.Instant;

/** DTOs de transporte desde proveedores externos (o mock). */
public final class ProviderDtos {
    private ProviderDtos() {}

    /**
     * Partido + cuotas 1X2 (con la casa que ofrece cada cuota). Los campos Over/Under son opcionales
     * (null si el proveedor no los ofrece). Doble oportunidad y BTTS se derivan del 1X2 en la ingesta.
     */
    public record ProviderMatch(
            String externalId,
            String equipoLocal,
            String equipoVisitante,
            Instant inicioUtc,
            Fase fase,
            BigDecimal cuotaLocal,
            BigDecimal cuotaEmpate,
            BigDecimal cuotaVisitante,
            String casaLocal,
            String casaEmpate,
            String casaVisitante,
            // Over/Under (totals). Null si no hay datos.
            BigDecimal lineaOU,
            BigDecimal cuotaOver,
            BigDecimal cuotaUnder,
            String casaOver,
            String casaUnder) {}

    /** Resultado final (a 90 min) entregado por el proveedor de resultados. */
    public record ProviderResult(
            String externalId,
            int golesLocal,
            int golesVisitante) {}
}
