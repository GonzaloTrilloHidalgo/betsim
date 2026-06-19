package com.betsim.provider;

import com.betsim.domain.Enums.Fase;

import java.math.BigDecimal;
import java.time.Instant;

/** DTOs de transporte desde proveedores externos (o mock). */
public final class ProviderDtos {
    private ProviderDtos() {}

    /** Una línea de Over/Under (totals): la línea (ej. 2.5) y la mejor cuota de cada lado con su casa. */
    public record OverUnder(
            java.math.BigDecimal linea,
            BigDecimal cuotaOver,
            BigDecimal cuotaUnder,
            String casaOver,
            String casaUnder) {}

    /**
     * Partido + cuotas 1X2 (con la casa que ofrece cada cuota) y, opcionalmente, varias líneas de
     * Over/Under. Doble oportunidad y BTTS se derivan del 1X2 en la ingesta.
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
            java.util.List<OverUnder> overUnder) {}

    /** Resultado final (a 90 min) entregado por el proveedor de resultados. */
    public record ProviderResult(
            String externalId,
            int golesLocal,
            int golesVisitante) {}
}
