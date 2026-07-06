package com.hermes.engine;

import com.hermes.domain.DegradationType;
import com.hermes.domain.IntegrationEdge;
import com.hermes.domain.SystemNode;

import java.util.List;

/**
 * The scoring model: where domain knowledge meets the graph.
 *
 * <p><b>System severity</b> = (businessCriticality × slaWeight + fanOut × 1.5
 * + orphanBonus) × degradationFactor. Business criticality is admin-entered
 * (how close is this system to revenue), slaWeight comes from the tightest
 * affected inbound SLA (a real-time feed failing hurts now, a batch feed
 * tonight), fanOut measures how far this node re-broadcasts the damage, and a
 * flat bonus marks total data starvation.</p>
 *
 * <p><b>Integration impact</b> = edgeCriticality × slaWeight
 * + downstreamConsumers × 2 + junctionBonus. An edge feeding a junction that
 * fans out to ten more systems outranks an equally critical edge feeding a
 * leaf.</p>
 *
 * <p>The weights are calibrated for a revenue-centric ERP landscape; a
 * pharma or banking landscape would weight SLA class and compliance-relevant
 * systems differently — which is exactly why they live in one class.</p>
 */
public final class CriticalityScorer {

    private static final double FAN_OUT_WEIGHT = 1.5;
    private static final double ORPHAN_BONUS = 5.0;
    private static final double DOWNSTREAM_WEIGHT = 2.0;
    private static final double JUNCTION_BONUS = 3.0;

    private static final double CRITICAL_THRESHOLD = 20.0;
    private static final double HIGH_THRESHOLD = 14.0;
    private static final double MEDIUM_THRESHOLD = 8.0;

    private CriticalityScorer() {
    }

    public static double systemSeverityScore(SystemNode node, int fanOut, boolean orphaned,
                                             List<IntegrationEdge> affectedInboundEdges,
                                             DegradationType degradationType) {
        double slaWeight = affectedInboundEdges.stream()
                .mapToDouble(e -> e.slaClass().weight())
                .max()
                .orElse(1.0);
        double raw = node.getBusinessCriticality() * slaWeight
                + fanOut * FAN_OUT_WEIGHT
                + (orphaned ? ORPHAN_BONUS : 0.0);
        return round(raw * degradationType.severityFactor());
    }

    public static Severity band(double severityScore) {
        if (severityScore >= CRITICAL_THRESHOLD) {
            return Severity.CRITICAL;
        }
        if (severityScore >= HIGH_THRESHOLD) {
            return Severity.HIGH;
        }
        if (severityScore >= MEDIUM_THRESHOLD) {
            return Severity.MEDIUM;
        }
        return Severity.LOW;
    }

    public static double integrationImpactScore(IntegrationEdge edge, int downstreamConsumersOfTarget,
                                                boolean targetIsLeaf, DegradationType degradationType) {
        double raw = edge.criticality() * edge.slaClass().weight()
                + downstreamConsumersOfTarget * DOWNSTREAM_WEIGHT
                + (targetIsLeaf ? 0.0 : JUNCTION_BONUS);
        return round(raw * degradationType.severityFactor());
    }

    /** The incident priority is set by the worst-hit system. */
    public static Priority overallPriority(List<AffectedSystem> affectedSystems) {
        Severity worst = affectedSystems.stream()
                .map(AffectedSystem::severity)
                .min(Enum::compareTo) // Severity is declared worst-first
                .orElse(Severity.LOW);
        return switch (worst) {
            case CRITICAL -> Priority.P1;
            case HIGH -> Priority.P2;
            case MEDIUM -> Priority.P3;
            case LOW -> Priority.P4;
        };
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
