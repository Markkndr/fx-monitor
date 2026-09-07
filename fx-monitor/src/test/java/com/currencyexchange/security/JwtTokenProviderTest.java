package com.currencyexchange.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    // HS512 requires a key of at least 512 bits (64 bytes).
    private static final String SECRET =
            "test-secret-key-that-is-definitely-long-enough-for-hs512-signing-0123456789";
    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour
    private static final long REFRESH_EXPIRATION_MS = 604_800_000L; // 7 days

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationMs", EXPIRATION_MS);
        ReflectionTestUtils.setField(tokenProvider, "refreshTokenExpirationMs", REFRESH_EXPIRATION_MS);
    }

    private UserDetails userDetails(String username) {
        return new User(username, "password", Collections.emptyList());
    }

    @Test
    @DisplayName("generateAccessToken produces a token carrying the subject and ACCESS type")
    void generatesAccessTokenFromUserDetails() {
        String token = tokenProvider.generateAccessToken(userDetails("alice@example.com"));

        assertThat(tokenProvider.getUsernameFromToken(token)).isEqualTo("alice@example.com");
        assertThat(tokenProvider.getTokenType(token)).isEqualTo("ACCESS");
        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("generateAccessToken with userId embeds the userId claim")
    void generatesAccessTokenWithUserId() {
        String token = tokenProvider.generateAccessToken("bob@example.com", 42L);

        assertThat(tokenProvider.getUsernameFromToken(token)).isEqualTo("bob@example.com");
        assertThat(tokenProvider.getUserIdFromToken(token)).isEqualTo(42L);
        assertThat(tokenProvider.getTokenType(token)).isEqualTo("ACCESS");
    }

    @Test
    @DisplayName("generateRefreshToken is tagged REFRESH and expires further out than an access token")
    void generatesRefreshToken() {
        String refresh = tokenProvider.generateRefreshToken("carol@example.com", 7L);
        String access = tokenProvider.generateAccessToken("carol@example.com", 7L);

        assertThat(tokenProvider.getTokenType(refresh)).isEqualTo("REFRESH");
        assertThat(tokenProvider.getUserIdFromToken(refresh)).isEqualTo(7L);
        assertThat(tokenProvider.getExpirationDateFromToken(refresh))
                .isAfter(tokenProvider.getExpirationDateFromToken(access));
    }

    @Test
    @DisplayName("getUserIdFromToken returns null when the claim is absent")
    void userIdAbsentReturnsNull() {
        String token = tokenProvider.generateAccessToken(userDetails("dave@example.com"));

        assertThat(tokenProvider.getUserIdFromToken(token)).isNull();
    }

    @Test
    @DisplayName("validateToken accepts a freshly minted token")
    void validateAcceptsValidToken() {
        String token = tokenProvider.generateAccessToken("eve@example.com", 1L);

        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken rejects a malformed token")
    void validateRejectsGarbage() {
        assertThat(tokenProvider.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    @DisplayName("validateToken rejects a token signed with a different secret")
    void validateRejectsForeignSignature() {
        JwtTokenProvider other = new JwtTokenProvider();
        ReflectionTestUtils.setField(other, "jwtSecret",
                "a-completely-different-secret-key-also-definitely-long-enough-for-hs512-xyz");
        ReflectionTestUtils.setField(other, "jwtExpirationMs", EXPIRATION_MS);
        ReflectionTestUtils.setField(other, "refreshTokenExpirationMs", REFRESH_EXPIRATION_MS);

        String foreignToken = other.generateAccessToken("mallory@example.com", 1L);

        assertThat(tokenProvider.validateToken(foreignToken)).isFalse();
    }

    @Test
    @DisplayName("validateToken rejects an already-expired token")
    void validateRejectsExpiredToken() {
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationMs", -1_000L);
        String expired = tokenProvider.generateAccessToken("frank@example.com", 1L);

        assertThat(tokenProvider.validateToken(expired)).isFalse();
    }

    @Test
    @DisplayName("isTokenExpired is false for a live token and true for an expired one")
    void isTokenExpiredReflectsExpiry() {
        String live = tokenProvider.generateAccessToken("grace@example.com", 1L);
        assertThat(tokenProvider.isTokenExpired(live)).isFalse();

        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationMs", -1_000L);
        String expired = tokenProvider.generateAccessToken("grace@example.com", 1L);
        assertThat(tokenProvider.isTokenExpired(expired)).isTrue();
    }

    @Test
    @DisplayName("getExpirationDateFromToken is set roughly one expiration window ahead")
    void expirationDateIsInTheFuture() {
        Date before = new Date();
        String token = tokenProvider.generateAccessToken("heidi@example.com", 1L);
        Date expiry = tokenProvider.getExpirationDateFromToken(token);

        assertThat(expiry).isAfter(before);
        assertThat(expiry.getTime() - before.getTime())
                .isLessThanOrEqualTo(EXPIRATION_MS + 5_000L);
    }

    @Test
    @DisplayName("getRemainingTime is positive for a live token")
    void remainingTimeIsPositive() {
        String token = tokenProvider.generateAccessToken("ivan@example.com", 1L);

        assertThat(tokenProvider.getRemainingTime(token))
                .isPositive()
                .isLessThanOrEqualTo(EXPIRATION_MS);
    }

    @Test
    @DisplayName("getAllClaimsFromToken exposes subject, type and userId")
    void allClaimsExposed() {
        String token = tokenProvider.generateAccessToken("judy@example.com", 99L);

        Claims claims = tokenProvider.getAllClaimsFromToken(token);

        assertThat(claims.getSubject()).isEqualTo("judy@example.com");
        assertThat(claims.get("type")).isEqualTo("ACCESS");
        assertThat(((Number) claims.get("userId")).longValue()).isEqualTo(99L);
    }
}
