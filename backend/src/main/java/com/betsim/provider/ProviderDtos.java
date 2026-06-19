package com.betsim.provider;

import com.betsim.domain.Enums.Fase;

import java.math.BigDecimal;
import java.time.Instant;

/** DTOs de transporte desde proveedores externos (o mock). */
public final class ProviderDtos {
    private ProviderDtos() {}

    /** Partido + cuotas 1X2 tal como lo entrega el proveedor de cuotas. */
    public record ProviderMatch(
            String externalId,
            String equipoLocal,
            String equipoVisitante,
            Instant inicioUtc,
            Fase fase,
            BigDecimal cuotaLocal,
            BigDecimal cuotaEmpate,
            BigDecimal cuotaVisitante) {}

    /** Resultado final (a 90 min) entregado por el proveedor de resultados. */
    public record ProviderResult(
            String externalId,
            int golesLocal,
            int golesVisitante) {}
}
