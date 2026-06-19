package com.betsim.api;

import com.betsim.domain.Enums.TipoTransaccion;
import com.betsim.domain.Transaccion;
import com.betsim.domain.Usuario;
import com.betsim.repository.TransaccionRepository;
import com.betsim.repository.UsuarioRepository;
import com.betsim.security.JwtService;
import com.betsim.web.ApiException;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UsuarioRepository usuarios;
    private final TransaccionRepository transacciones;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final BigDecimal startingBalance;

    public AuthController(UsuarioRepository usuarios, TransaccionRepository transacciones,
                         PasswordEncoder encoder, JwtService jwt,
                         @Value("${betsim.economy.starting-balance}") BigDecimal startingBalance) {
        this.usuarios = usuarios;
        this.transacciones = transacciones;
        this.encoder = encoder;
        this.jwt = jwt;
        this.startingBalance = startingBalance;
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 30) String username,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Size(min = 6, max = 72) String password) {}

    public record LoginRequest(@NotBlank String usernameOrEmail, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record AuthResponse(String accessToken, String refreshToken, String username, BigDecimal saldo) {}

    @PostMapping("/register")
    @Transactional
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        if (usuarios.existsByUsername(req.username())) throw ApiException.conflict("El usuario ya existe");
        if (usuarios.existsByEmailIgnoreCase(req.email())) throw ApiException.conflict("El email ya está registrado");

        Usuario u = new Usuario();
        u.setUsername(req.username());
        u.setEmail(req.email());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setSaldo(startingBalance);
        usuarios.save(u);

        transacciones.save(new Transaccion(u, null, TipoTransaccion.BONO_INICIAL, startingBalance, startingBalance));
        return tokens(u);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        Usuario u = usuarios.findByUsername(req.usernameOrEmail())
                .or(() -> usuarios.findByEmailIgnoreCase(req.usernameOrEmail()))
                .orElseThrow(() -> ApiException.unauthorized("Credenciales inválidas"));
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw ApiException.unauthorized("Credenciales inválidas");
        }
        return tokens(u);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        try {
            Claims c = jwt.parse(req.refreshToken());
            if (!"refresh".equals(c.get("type", String.class))) throw ApiException.unauthorized("Token inválido");
            Usuario u = usuarios.findById(Long.valueOf(c.getSubject()))
                    .orElseThrow(() -> ApiException.unauthorized("Usuario no encontrado"));
            return tokens(u);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.unauthorized("Refresh token inválido o expirado");
        }
    }

    private AuthResponse tokens(Usuario u) {
        String access = jwt.generateAccess(u.getId(), u.getUsername(), u.getRol().name());
        String refresh = jwt.generateRefresh(u.getId(), u.getUsername());
        return new AuthResponse(access, refresh, u.getUsername(), u.getSaldo());
    }
}
