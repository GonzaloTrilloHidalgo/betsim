package com.betsim.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                if ("access".equals(claims.get("type", String.class))
                        && SecurityContextHolder.getContext().getAuthentication() == null) {
                    Long userId = Long.valueOf(claims.getSubject());
                    String rol = claims.get("rol", String.class);
                    var authorities = List.of(new SimpleGrantedAuthority(
                            "ROLE_" + (rol == null ? "USER" : rol)));
                    var principal = new AuthPrincipal(userId, claims.get("username", String.class));
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                // Token inválido/expirado -> se queda sin autenticar; endpoints protegidos darán 401.
            }
        }
        chain.doFilter(request, response);
    }

    /** Principal ligero con el id del usuario autenticado. */
    public record AuthPrincipal(Long userId, String username) {}
}
