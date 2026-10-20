package com.seckill.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void generatedTokenCanBeParsed() {
        JwtUtil util = new JwtUtil(SECRET, 60);

        String token = util.generate(42L, "alice");
        Claims claims = util.parse(token);

        assertEquals("alice", claims.getSubject());
        assertEquals(42L, ((Number) claims.get("userId")).longValue());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtUtil issuer = new JwtUtil(SECRET, 60);
        JwtUtil verifier = new JwtUtil("ffffffffffffffffffffffffffffffff", 60);

        String token = issuer.generate(1L, "alice");

        assertThrows(Exception.class, () -> verifier.parse(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtUtil util = new JwtUtil(SECRET, 60);
        String token = util.generate(1L, "alice");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThrows(Exception.class, () -> util.parse(tampered));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtil util = new JwtUtil(SECRET, -1);
        String token = util.generate(1L, "alice");

        assertThrows(Exception.class, () -> util.parse(token));
    }
}
