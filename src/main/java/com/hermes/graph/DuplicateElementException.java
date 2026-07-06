package com.hermes.graph;

/** Thrown when a system or integration id is registered twice. */
public class DuplicateElementException extends RuntimeException {
    public DuplicateElementException(String message) {
        super(message);
    }
}
