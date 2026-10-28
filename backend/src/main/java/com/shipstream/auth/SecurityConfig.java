package com.shipstream.auth;

import java.util.List;
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

    SecurityConfig(JwtAuthenticationFilter jwtFilter, ProblemDetailAuthHandler problemDetailHandler) {
        this.jwtFilter = jwtFilter;
        this.problemDetailHandler = problemDetailHandler;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Stateless JWT-in-cookie API, not a browser form session: nothing here accepts a
                // state-changing GET, and the cookie is SameSite=Lax, which blocks it being sent on a
                // cross-site POST in the first place - the usual reason CSRF protection exists.
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
        // 5173 = `npm run dev`, 4173 = `npm run preview` (serves the production build locally)
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:4173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // the JWT travels as a cookie, not a header

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
