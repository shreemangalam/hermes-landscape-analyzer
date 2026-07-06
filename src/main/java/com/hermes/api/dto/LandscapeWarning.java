package com.hermes.api.dto;

/** A structural anomaly detected in the landscape graph. */
public record LandscapeWarning(WarningType type, String message) {

    public enum WarningType {
        /** A cyclic dependency between systems — traversals stay safe, but operations should know. */
        CYCLE,
        /** A consumer fed by exactly one source: an outage there means complete data starvation. */
        SINGLE_SOURCE_CONSUMER,
        /** A registered system with no integrations at all — likely stale configuration. */
        ISOLATED_SYSTEM
    }
}
