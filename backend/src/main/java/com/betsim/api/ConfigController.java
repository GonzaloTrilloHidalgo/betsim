package com.betsim.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** Parámetros de juego que el frontend necesita conocer (límites, economía). */
@RestController
@RequestMapping("/api/v1")
public class ConfigController {

    private final int maxSelecciones;
    private final BigDecimal dailyBonus;
    private final BigDecimal resetThreshold;

    public ConfigController(@Value("${betsim.bet.max-selecciones}") int maxSelecciones,
                            @Value("${betsim.economy.daily-bonus}") BigDecimal dailyBonus,
                            @Value("${betsim.economy.reset-threshold}") BigDecimal resetThreshold) {
        this.maxSelecciones = maxSelecciones;
        this.dailyBonus = dailyBonus;
        this.resetThreshold = resetThreshold;
    }

    public record ConfigView(int maxSelecciones, BigDecimal dailyBonus, BigDecimal resetThreshold) {}

    @GetMapping("/config")
    public ConfigView config() {
        return new ConfigView(maxSelecciones, dailyBonus, resetThreshold);
    }
}
