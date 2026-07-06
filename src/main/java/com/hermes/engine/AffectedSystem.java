package com.hermes.engine;

/** One system reached by the failure propagation, with its classification. */
public record AffectedSystem(
        String systemId,
        String name,
        int hopDistance,
        ImpactType impactType,
        SourcingStatus sourcingStatus,
        Severity severity,
        double severityScore,
        int businessCriticality) {
}
