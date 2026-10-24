package com.shipstream.common;

/** Thrown when a shipment status transition is not allowed by the state machine. */
public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(String message) {
        super(message);
    }
}
