package com.currencyexchange.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JwtTokenProvider {

    /** Blank unless JWT_SECRET is set; {@link #init()} then falls back to the per-user secret file. */
    @Value("${spring.security.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.security.jwt.secret-file:${user.home}/.fx-monitor/jwt-secret}")
    private String jwtSecretFile;

    @Value("${spring.security.jwt.expiration:900000}")
    private long jwtExpirationMs; // 15 minutes default

    @Value("${spring.security.jwt.refresh-expiration:604800000}")
    private long refreshTokenExpirationMs; // 7 days default

    @PostConstruct
    void init() {
        jwtSecret = JwtSecretResolver.resolve(jwtSecret, Path.of(jwtSecretFile));
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "ACCESS");
        return createToken(claims, userDetails.getUsername(), jwtExpirationMs);
    }

    public String generateAccessToken(String username, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "ACCESS");
        claims.put("userId", userId);
        return createToken(claims, username, jwtExpirationMs);
    }

    public String generateRefreshToken(String username, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "REFRESH");
        claims.put("userId", userId);
        return createToken(claims, username, refreshTokenExpirationMs);
    }

    private String createToken(Map<String, Object> claims, String subject, long expirationTime) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return getAllClaimsFromToken(token).getSubject();
    }

    public Long getUserIdFromToken(String token) {
        Object userId = getAllClaimsFromToken(token).get("userId");
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        return null;
    }

    public String getTokenType(String token) {
        return (String) getAllClaimsFromToken(token).get("type");
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    public boolean isTokenExpired(String token) {
        try {
            return getAllClaimsFromToken(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    public Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Date getExpirationDateFromToken(String token) {
        return getAllClaimsFromToken(token).getExpiration();
    }

    public long getRemainingTime(String token) {
        return getAllClaimsFromToken(token).getExpiration().getTime() - System.currentTimeMillis();
    }
}