package com.betsim.api;

import com.betsim.domain.Transaccion;
import com.betsim.domain.Usuario;
import com.betsim.repository.TransaccionRepository;
import com.betsim.security.JwtAuthFilter.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final WalletService wallet;
    private final TransaccionRepository transacciones;

    public WalletController(WalletService wallet, TransaccionRepository transacciones) {
        this.wallet = wallet;
        this.transacciones = transacciones;
    }

    public record WalletView(BigDecimal saldo, String username, boolean bonoDisponible, Instant proximoBono) {}
    public record TxView(Long id, String tipo, BigDecimal importe, BigDecimal saldoResultante, Instant fecha) {}

    @GetMapping
    public WalletView get(@AuthenticationPrincipal AuthPrincipal me) {
        return walletView(wallet.require(me.userId()));
    }

    /** Construye la vista de billetera incluyendo si el bono está disponible y cuándo toca el siguiente. */
    private WalletView walletView(Usuario u) {
        LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
        boolean disponible = u.getUltimoBono() == null || !u.getUltimoBono().equals(hoy);
        Instant proximo = disponible ? null : hoy.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new WalletView(u.getSaldo(), u.getUsername(), disponible, proximo);
    }

    @GetMapping("/transactions")
    public List<TxView> transactions(@AuthenticationPrincipal AuthPrincipal me) {
        return transacciones.findByUsuarioIdOrderByCreadoEnAsc(me.userId()).stream()
                .map(this::toView).toList();
    }

    @PostMapping("/daily-bonus")
    public WalletView dailyBonus(@AuthenticationPrincipal AuthPrincipal me) {
        return walletView(wallet.claimDailyBonus(me.userId()));
    }

    @PostMapping("/reset")
    public WalletView reset(@AuthenticationPrincipal AuthPrincipal me) {
        return walletView(wallet.reset(me.userId()));
    }

    private TxView toView(Transaccion t) {
        return new TxView(t.getId(), t.getTipo().name(), t.getImporte(), t.getSaldoResultante(), t.getCreadoEn());
    }
}
