package com.linklife.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-key-with-at-least-32-bytes!!", 168, 30);

    @Test
    void accessTokenRoundTrip() {
        String token = jwtService.generateAccessToken(42L);
        TokenInfo info = jwtService.parse(token);
        assertEquals(42L, info.userId());
        assertEquals("access", info.type());
    }

    @Test
    void refreshTokenHasRefreshType() {
        String token = jwtService.generateRefreshToken(42L);
        TokenInfo info = jwtService.parse(token);
        assertEquals("refresh", info.type());
    }

    @Test
    void invalidTokenRejected() {
        assertThrows(JwtException.class, () -> jwtService.parse("not-a-token"));
    }
}
