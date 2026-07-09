package com.sentinelhub.security;

import com.sentinelhub.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            new JwtProperties("sentinelhub-test-jwt-secret-32bytes!!", 24)
    );

    @Test
    void createAndParseToken_roundTrip() {
        String token = jwtService.createToken("user-1", "tenant-1", "admin@test.local", List.of("admin"));

        Claims claims = jwtService.parse(token);
        assertEquals("user-1", claims.getSubject());
        assertEquals("tenant-1", claims.get("tenant_id", String.class));
        assertEquals("admin@test.local", claims.get("email", String.class));
        assertNotNull(claims.getExpiration());
    }
}
