package com.shipstream.auth;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The public/authenticated split that justifies this having a login at all: a stranger can look up a
 * shipment by tracking ID (matches how every real carrier's public site works) but can't create,
 * cancel, or re-route anything, or touch hub/route data, without an OPS login.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ProblemDetailAuthHandler problemDetailHandler;
    private final List<String> allowedOrigins;

    SecurityConfig(JwtAuthenticationFilter jwtFilter, ProblemDetailAuthHandler problemDetailHandler,
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtFilter = jwtFilter;
        this.problemDetailHandler = problemDetailHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // What actually prevents CSRF here doesn't depend on SameSite (which is configurable -
                // see AuthController - and becomes None, not Lax, once frontend and backend are on
                // different domains): every state-changing request is JSON (Content-Type:
                // application/json), which isn't a CORS "simple" header, so the browser always sends a
                // preflight first; CorsConfigurationSource only allows the exact configured origin(s),
                // never "*". An attacker's site simply can't get a credentialed request past that
                // preflight to begin with, regardless of SameSite.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemDetailHandler)
                        .accessDeniedHandler(problemDetailHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/shipments/track/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tracking/*").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Defaults (application.properties) cover local dev (5173 = `npm run dev`, 4173 = `npm run
        // preview`); set CORS_ALLOWED_ORIGINS (comma-separated) to the deployed frontend's origin(s).
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // the JWT travels as a cookie, not a header

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
