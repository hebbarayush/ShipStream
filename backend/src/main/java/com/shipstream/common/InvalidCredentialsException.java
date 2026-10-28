package com.shipstream.common;

/** Thrown on a failed login attempt (unknown username or wrong password - deliberately not distinguished). */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
