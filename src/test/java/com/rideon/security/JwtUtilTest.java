package com.rideon.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    private static final String TEST_KEY =
            "404D635166546A576E5A7234753778214125442A472D4B6150645367566B5970";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_KEY, 86400000L);
    }

    @Test
    void generateToken_returnsNonBlankToken() {
        String token = jwtUtil.generateToken("rider@example.com");
        assertThat(token).isNotBlank();
    }

    @Test
    void extractEmail_returnsCorrectEmail_afterGenerate() {
        String token = jwtUtil.generateToken("rider@example.com");
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("rider@example.com");
    }

    @Test
    void isTokenValid_returnsTrue_forFreshToken() {
        String token = jwtUtil.generateToken("rider@example.com");
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalse_forTamperedToken() {
        String token = jwtUtil.generateToken("rider@example.com");
        String tampered = token.substring(0, token.length() - 4) + "xxxx";
        assertThat(jwtUtil.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_returnsFalse_forExpiredToken() {
        JwtUtil shortLivedUtil = new JwtUtil(TEST_KEY, -1000L); // already expired
        String token = shortLivedUtil.generateToken("rider@example.com");
        assertThat(shortLivedUtil.validateToken(token)).isFalse();
    }
}