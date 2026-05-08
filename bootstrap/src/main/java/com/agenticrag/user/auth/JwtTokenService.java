package com.agenticrag.user.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class JwtTokenService {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        Assert.hasText(jwtProperties.getSecret(), "JWT secret cannot be empty");
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public TokenPair generateTokenPair(String userId, String username, String role) {
        String accessToken = buildToken(userId, username, role, AuthConstants.ACCESS_TOKEN_TYPE, jwtProperties.getAccessExpireSeconds());
        String refreshToken = buildToken(userId, username, role, AuthConstants.REFRESH_TOKEN_TYPE, jwtProperties.getRefreshExpireSeconds());
        return new TokenPair(accessToken, refreshToken);
    }

    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, AuthConstants.ACCESS_TOKEN_TYPE);
        return toAuthenticatedUser(claims);
    }

    public AuthenticatedUser parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, AuthConstants.REFRESH_TOKEN_TYPE);
        return toAuthenticatedUser(claims);
    }

    public Instant getExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String buildToken(String userId, String username, String role, String tokenType, long expireSeconds) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expireSeconds);
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .claim("tokenType", tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    private void validateTokenType(Claims claims, String expectedType) {
        String tokenType = claims.get("tokenType", String.class);
        if (!expectedType.equals(tokenType)) {
            throw new IllegalArgumentException("Token type is invalid");
        }
    }

    private AuthenticatedUser toAuthenticatedUser(Claims claims) {
        return new AuthenticatedUser(
                claims.getSubject(),
                claims.get("username", String.class),
                claims.get("role", String.class)
        );
    }

    public static class TokenPair {
        private final String accessToken;
        private final String refreshToken;

        public TokenPair(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }
    }
}
