package com.linklife.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(@Value("${link.jwt.secret}") String secret,
                      @Value("${link.jwt.access-ttl-hours}") long accessTtlHours,
                      @Value("${link.jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTtl = Duration.ofHours(accessTtlHours);
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
    }

    public String generateAccessToken(long userId) {
        return build(userId, "access", accessTtl);
    }

    public String generateRefreshToken(long userId) {
        return build(userId, "refresh", refreshTtl);
    }

    public TokenInfo parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return new TokenInfo(Long.parseLong(claims.getSubject()), claims.get("type", String.class));
    }

    private String build(long userId, String type, Duration ttl) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key)
                .compact();
    }
}
