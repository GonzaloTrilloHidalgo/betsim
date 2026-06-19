package com.betsim.api;

import com.betsim.scheduler.IngestService;
import com.betsim.scheduler.SettlementService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final IngestService ingest;
    private final SettlementService settlement;

    public AdminController(IngestService ingest, SettlementService settlement) {
        this.ingest = ingest;
        this.settlement = settlement;
    }

    /** Fuerza sincronización + liquidación manual (rol ADMIN). */
    @PostMapping("/sync")
    public Map<String, Object> sync() {
        int procesados = ingest.sync();
        settlement.run();
        return Map.of("partidosSincronizados", procesados, "estado", "ok");
    }
}
