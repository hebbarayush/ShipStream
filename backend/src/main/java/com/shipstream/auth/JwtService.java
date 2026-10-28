package com.shipstream.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies the HS256 JWT carried in the {@value #COOKIE_NAME} cookie. Stateless by design
 * (no server-side session table) - the token itself is the session, valid until it expires or the
 * secret changes.
 */
@Component
public class JwtService {

    static final String COOKIE_NAME = "shipstream_token";
    private static final Duration TTL = Duration.ofHours(12);
    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;

    JwtService(@Value("${app.security.jwt-secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    String issue(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TTL)))
                .signWith(key)
                .compact();
    }

    Duration ttl() {
        return TTL;
    }

    public record Principal(String username, String role) {}

    public Optional<Principal> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new Principal(claims.getSubject(), claims.get(ROLE_CLAIM, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
