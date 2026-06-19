package com.betsim.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tareas programadas (ARQUITECTURA_Y_PLAN.md §6). En el MVP con mock, calendario y cuotas comparten
 * la misma ingesta. Las frecuencias están ajustadas al presupuesto del free tier.
 */
@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final IngestService ingest;
    private final SettlementService settlement;

    public ScheduledJobs(IngestService ingest, SettlementService settlement) {
        this.ingest = ingest;
        this.settlement = settlement;
    }

    /** Sincronizador de calendario: diario a las 01:00 UTC. */
    @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
    public void calendario() {
        log.info("[job] Sincronizando calendario...");
        ingest.sync();
    }

    /** Actualizador de cuotas: cada 30 min. */
    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT30M")
    public void cuotas() {
        log.info("[job] Actualizando cuotas...");
        ingest.sync();
    }

    /** Motor de liquidación: cada 5 min. */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void liquidacion() {
        log.debug("[job] Motor de liquidación...");
        settlement.run();
    }
}
