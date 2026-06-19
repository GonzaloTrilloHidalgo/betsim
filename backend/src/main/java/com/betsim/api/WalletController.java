package com.betsim.api;

import com.betsim.domain.Transaccion;
import com.betsim.domain.Usuario;
import com.betsim.repository.TransaccionRepository;
import com.betsim.security.JwtAuthFilter.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
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

    public record WalletView(BigDecimal saldo, String username) {}
    public record TxView(Long id, String tipo, BigDecimal importe, BigDecimal saldoResultante, Instant fecha) {}

    @GetMapping
    public WalletView get(@AuthenticationPrincipal AuthPrincipal me) {
        Usuario u = wallet.require(me.userId());
        return new WalletView(u.getSaldo(), u.getUsername());
    }

    @GetMapping("/transactions")
    public List<TxView> transactions(@AuthenticationPrincipal AuthPrincipal me) {
        return transacciones.findByUsuarioIdOrderByCreadoEnAsc(me.userId()).stream()
                .map(this::toView).toList();
    }

    @PostMapping("/daily-bonus")
    public WalletView dailyBonus(@AuthenticationPrincipal AuthPrincipal me) {
        Usuario u = wallet.claimDailyBonus(me.userId());
        return new WalletView(u.getSaldo(), u.getUsername());
    }

    @PostMapping("/reset")
    public WalletView reset(@AuthenticationPrincipal AuthPrincipal me) {
        Usuario u = wallet.reset(me.userId());
        return new WalletView(u.getSaldo(), u.getUsername());
    }

    private TxView toView(Transaccion t) {
        return new TxView(t.getId(), t.getTipo().name(), t.getImporte(), t.getSaldoResultante(), t.getCreadoEn());
    }
}
