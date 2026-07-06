package com.hermes.service;

/** Thrown when a requested impact report id does not exist (or was evicted). */
public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException(String message) {
        super(message);
    }
}
