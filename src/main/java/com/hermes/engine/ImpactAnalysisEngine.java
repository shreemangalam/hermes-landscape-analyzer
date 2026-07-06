package com.hermes.engine;

import com.hermes.domain.DegradationType;
import com.hermes.domain.IntegrationEdge;
import com.hermes.domain.SystemNode;
import com.hermes.graph.CycleDetector;
import com.hermes.graph.IntegrationGraph;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Forward breadth-first impact propagation from a failed system.
 *
 * <p>BFS (not DFS) on purpose: BFS discovers systems in "impact rings" — every
 * node's hop distance is the shortest path from the failure, which maps
 * directly to the DIRECT (1 hop) vs CASCADE (2+ hops) classification an
 * operations team thinks in. The visited set makes the traversal cycle-safe;
 * cycles are reported as landscape warnings, never followed forever.</p>
 */
public final class ImpactAnalysisEngine {

    private ImpactAnalysisEngine() {
    }

    public static ImpactReport analyze(IntegrationGraph graph, String failedSystemId,
                                       DegradationType degradationType) {
        long startNanos = System.nanoTime();
        SystemNode failed = graph.requireNode(failedSystemId);

        // --- BFS: ring-by-ring propagation, recording shortest hop distance ---
        Map<String, Integer> hopDistance = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        visited.add(failedSystemId);
        Queue<String> queue = new ArrayDeque<>();
        queue.add(failedSystemId);
        hopDistance.put(failedSystemId, 0);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (IntegrationEdge edge : graph.outgoingEdges(current)) {
                if (visited.add(edge.targetId())) {
                    hopDistance.put(edge.targetId(), hopDistance.get(current) + 1);
                    queue.add(edge.targetId());
                }
            }
        }

        Set<String> affectedOrFailed = visited;

        // --- Classify each affected system ---
        List<AffectedSystem> affectedSystems = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : hopDistance.entrySet()) {
            if (entry.getKey().equals(failedSystemId)) {
                continue;
            }
            SystemNode node = graph.requireNode(entry.getKey());
            int hops = entry.getValue();

            List<IntegrationEdge> inbound = graph.incomingEdges(node.getId());
            List<IntegrationEdge> affectedInbound = inbound.stream()
                    .filter(e -> affectedOrFailed.contains(e.sourceId()))
                    .toList();
            boolean orphaned = affectedInbound.size() == inbound.size();

            double score = CriticalityScorer.systemSeverityScore(
                    node, graph.fanOut(node.getId()), orphaned, affectedInbound, degradationType);

            affectedSystems.add(new AffectedSystem(
                    node.getId(),
                    node.getName(),
                    hops,
                    hops == 1 ? ImpactType.DIRECT : ImpactType.CASCADE,
                    orphaned ? SourcingStatus.ORPHANED : SourcingStatus.PARTIAL,
                    CriticalityScorer.band(score),
                    score,
                    node.getBusinessCriticality()));
        }
        affectedSystems.sort(Comparator.comparingDouble(AffectedSystem::severityScore).reversed()
                .thenComparing(AffectedSystem::systemId));

        // --- Rank every affected integration ---
        // Every outgoing edge of an affected-or-failed system targets an affected
        // system (BFS guarantees it), so all of them carry impact.
        Map<String, Integer> downstreamCache = new HashMap<>();
        List<AffectedIntegration> integrations = new ArrayList<>();
        List<IntegrationEdge> impactedEdges = affectedOrFailed.stream()
                .flatMap(id -> graph.outgoingEdges(id).stream())
                .sorted(Comparator.comparing(IntegrationEdge::id))
                .toList();
        List<ScoredEdge> scored = new ArrayList<>();
        for (IntegrationEdge edge : impactedEdges) {
            int downstream = downstreamCache.computeIfAbsent(
                    edge.targetId(), id -> graph.downstreamOf(id).size());
            double score = CriticalityScorer.integrationImpactScore(
                    edge, downstream, graph.isLeaf(edge.targetId()), degradationType);
            scored.add(new ScoredEdge(edge, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredEdge::score).reversed()
                .thenComparing(s -> s.edge().id()));
        for (int i = 0; i < scored.size(); i++) {
            IntegrationEdge edge = scored.get(i).edge();
            integrations.add(new AffectedIntegration(
                    i + 1, edge.id(), edge.name(), edge.sourceId(), edge.targetId(),
                    edge.protocol(), edge.businessProcess(), edge.slaClass(), scored.get(i).score()));
        }

        // --- Recovery sequence and landscape warnings ---
        List<RecoveryStep> recovery = RecoverySequencer.sequence(graph, failedSystemId,
                hopDistance.keySet());

        List<String> warnings = CycleDetector.findCycles(graph).stream()
                .filter(cycle -> cycle.stream().anyMatch(affectedOrFailed::contains))
                .map(cycle -> "Cycle detected: " + String.join(" -> ", cycle) + " -> " + cycle.get(0))
                .toList();

        // --- Aggregate statistics ---
        int direct = (int) affectedSystems.stream().filter(a -> a.impactType() == ImpactType.DIRECT).count();
        int orphanedCount = (int) affectedSystems.stream()
                .filter(a -> a.sourcingStatus() == SourcingStatus.ORPHANED).count();
        int maxDepth = affectedSystems.stream().mapToInt(AffectedSystem::hopDistance).max().orElse(0);

        Priority priority = CriticalityScorer.overallPriority(affectedSystems);
        ImpactStatistics stats = new ImpactStatistics(
                affectedSystems.size(),
                direct,
                affectedSystems.size() - direct,
                orphanedCount,
                affectedSystems.size() - orphanedCount,
                maxDepth,
                integrations.size(),
                (System.nanoTime() - startNanos) / 1_000);

        return new ImpactReport(
                UUID.randomUUID().toString(),
                Instant.now(),
                failed.getId(),
                failed.getName(),
                degradationType,
                priority,
                priority + " — " + priority.description(),
                affectedSystems,
                integrations,
                recovery,
                warnings,
                stats);
    }

    private record ScoredEdge(IntegrationEdge edge, double score) {
    }
}
