package com.shipstream.auth;

import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record UserResponse(String username, String role) {
        static UserResponse from(User u) {
            return new UserResponse(u.getUsername(), u.getRole());
        }
    }
}
