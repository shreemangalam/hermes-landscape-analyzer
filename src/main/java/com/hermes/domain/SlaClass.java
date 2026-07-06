package com.hermes.domain;

/**
 * Delivery expectation of an integration. The weight feeds the impact score:
 * losing a real-time interface hurts immediately, losing a nightly batch
 * interface buys you hours of recovery time.
 */
public enum SlaClass {
    REAL_TIME(1.5),
    NEAR_REAL_TIME(1.2),
    BATCH(1.0);

    private final double weight;

    SlaClass(double weight) {
        this.weight = weight;
    }

    public double weight() {
        return weight;
    }
}
