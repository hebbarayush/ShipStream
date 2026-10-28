package com.shipstream.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Keeps 401/403 responses in the same {@code ProblemDetail} shape as every other API error
 * ({@link com.shipstream.common.ApiExceptionHandler}) instead of Spring Security's default empty body.
 * These two cases happen inside the filter chain, before the controller layer, so
 * {@code @ExceptionHandler} can't reach them - hence the separate handler.
 */
@Component
class ProblemDetailAuthHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    ProblemDetailAuthHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws java.io.IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Login required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws java.io.IOException {
        write(response, HttpStatus.FORBIDDEN, "Not allowed");
    }

    private void write(HttpServletResponse response, HttpStatus status, String detail) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        mapper.writeValue(response.getWriter(), body);
    }
}
