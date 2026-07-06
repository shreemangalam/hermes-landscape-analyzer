package com.hermes.engine;

import com.hermes.domain.DegradationType;

import java.time.Instant;
import java.util.List;

/** The full result of an impact analysis run. */
public record ImpactReport(
        String reportId,
        Instant generatedAt,
        String degradedSystemId,
        String degradedSystemName,
        DegradationType degradationType,
        Priority priority,
        String prioritySummary,
        List<AffectedSystem> affectedSystems,
        List<AffectedIntegration> affectedIntegrations,
        List<RecoveryStep> recommendedRecoverySequence,
        List<String> landscapeWarnings,
        ImpactStatistics statistics) {
}
