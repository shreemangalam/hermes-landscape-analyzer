package com.hermes.api.dto;

import java.util.List;
import java.util.Map;

/** Aggregate statistics over the whole landscape graph. */
public record LandscapeStatistics(
        int totalSystems,
        int totalIntegrations,
        Map<String, Long> protocolDistribution,
        Map<String, Long> slaDistribution,
        double averageFanOut,
        int junctionCount,
        int leafCount,
        int cycleCount,
        List<CriticalSystem> topCriticalSystems) {

    /** Systems ranked by criticality x connectivity — the ones to watch. */
    public record CriticalSystem(String id, String name, int businessCriticality, int fanOut, double score) {
    }
}
