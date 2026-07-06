package com.hermes.domain;

/** How badly the analyzed system has failed. Scales the overall impact score. */
public enum DegradationType {
    COMPLETE_OUTAGE(1.0),
    PARTIAL_DEGRADATION(0.7),
    PERFORMANCE_DEGRADATION(0.4);

    private final double severityFactor;

    DegradationType(double severityFactor) {
        this.severityFactor = severityFactor;
    }

    public double severityFactor() {
        return severityFactor;
    }
}
