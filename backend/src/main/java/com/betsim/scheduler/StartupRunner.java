package com.betsim.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Carga datos iniciales al arrancar para que la app tenga cartelera desde el primer momento. */
@Component
public class StartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    private final IngestService ingest;
    private final SettlementService settlement;

    public StartupRunner(IngestService ingest, SettlementService settlement) {
        this.ingest = ingest;
        this.settlement = settlement;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ingest.sync();
            settlement.run();
            log.info("Datos iniciales cargados.");
        } catch (Exception e) {
            log.warn("No se pudo cargar datos iniciales al arranque: {}", e.getMessage());
        }
    }
}
