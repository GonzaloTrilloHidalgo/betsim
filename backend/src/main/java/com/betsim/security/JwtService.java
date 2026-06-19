package com.betsim.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlMinutes;
    private final long refreshTtlDays;

    public JwtService(@Value("${betsim.jwt.secret}") String secret,
                      @Value("${betsim.jwt.access-ttl-minutes}") long accessTtlMinutes,
                      @Value("${betsim.jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMinutes = accessTtlMinutes;
        this.refreshTtlDays = refreshTtlDays;
    }

    public String generateAccess(Long userId, String username, String rol) {
        return build(userId, username, "access", Instant.now().plus(accessTtlMinutes, ChronoUnit.MINUTES), rol);
    }

    public String generateRefresh(Long userId, String username) {
        return build(userId, username, "refresh", Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS), null);
    }

    private String build(Long userId, String username, String type, Instant exp, String rol) {
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", type)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(exp));
        if (rol != null) builder.claim("rol", rol);
        return builder.signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
