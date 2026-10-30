package com.shipstream.auth;

import com.shipstream.auth.AuthDtos.LoginRequest;
import com.shipstream.auth.AuthDtos.UserResponse;
import com.shipstream.common.InvalidCredentialsException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt,
            @Value("${app.security.cookie-secure:false}") boolean cookieSecure,
            // Lax is correct (and safer) when frontend and backend share a registrable domain, e.g.
            // local dev (both localhost) or same-domain production. It breaks the moment they're on
            // genuinely different domains (e.g. a Vercel frontend + a Render backend): Lax cookies
            // aren't sent on cross-site fetch/XHR at all, so login would silently never attach the
            // cookie. None (which *requires* Secure=true - browsers reject None without it) is what
            // that split-domain topology needs instead.
            @Value("${app.security.cookie-same-site:Lax}") String cookieSameSite) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/login")
    ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByUsername(req.username())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        String token = jwt.issue(user.getUsername(), user.getRole());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(token, jwt.ttl().getSeconds()).toString())
                .body(UserResponse.from(user));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", 0).toString())
                .build();
    }

    /** Protected (see SecurityConfig): a 401 here *is* the "not logged in" signal, nothing to catch. */
    @GetMapping("/me")
    UserResponse me(Authentication auth) {
        User user = users.findByUsername(auth.getName())
                .orElseThrow(() -> new InvalidCredentialsException("Session no longer valid"));
        return UserResponse.from(user);
    }

    private ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(JwtService.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
