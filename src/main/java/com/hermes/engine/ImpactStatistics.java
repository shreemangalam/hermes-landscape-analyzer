package com.hermes.engine;

/** Aggregate numbers for an impact report. */
public record ImpactStatistics(
        int totalAffectedSystems,
        int directImpactCount,
        int cascadeImpactCount,
        int orphanedConsumerCount,
        int partiallyDegradedCount,
        int maxCascadeDepth,
        int affectedIntegrationCount,
        long analysisDurationMicros) {
}
