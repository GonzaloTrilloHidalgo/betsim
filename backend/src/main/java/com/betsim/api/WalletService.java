package com.betsim.api;

import com.betsim.domain.Enums.TipoTransaccion;
import com.betsim.domain.Transaccion;
import com.betsim.domain.Usuario;
import com.betsim.repository.TransaccionRepository;
import com.betsim.repository.UsuarioRepository;
import com.betsim.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class WalletService {

    private final UsuarioRepository usuarios;
    private final TransaccionRepository transacciones;
    private final BigDecimal startingBalance;
    private final BigDecimal dailyBonus;

    public WalletService(UsuarioRepository usuarios, TransaccionRepository transacciones,
                         @Value("${betsim.economy.starting-balance}") BigDecimal startingBalance,
                         @Value("${betsim.economy.daily-bonus}") BigDecimal dailyBonus) {
        this.usuarios = usuarios;
        this.transacciones = transacciones;
        this.startingBalance = startingBalance;
        this.dailyBonus = dailyBonus;
    }

    public Usuario require(Long userId) {
        // Si el principal autenticado ya no existe (p. ej. BD reiniciada), la sesión no es válida -> 401.
        return usuarios.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Sesión no válida: vuelve a iniciar sesión"));
    }

    @Transactional
    public Usuario claimDailyBonus(Long userId) {
        Usuario u = require(userId);
        LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
        if (hoy.equals(u.getUltimoBono())) {
            throw ApiException.conflict("El bono diario ya se ha reclamado hoy");
        }
        u.setSaldo(u.getSaldo().add(dailyBonus));
        u.setUltimoBono(hoy);
        usuarios.save(u);
        transacciones.save(new Transaccion(u, null, TipoTransaccion.BONO_DIARIO, dailyBonus, u.getSaldo()));
        return u;
    }

    @Transactional
    public Usuario reset(Long userId) {
        Usuario u = require(userId);
        BigDecimal delta = startingBalance.subtract(u.getSaldo());
        u.setSaldo(startingBalance);
        usuarios.save(u);
        transacciones.save(new Transaccion(u, null, TipoTransaccion.RESET, delta, u.getSaldo()));
        return u;
    }
}
