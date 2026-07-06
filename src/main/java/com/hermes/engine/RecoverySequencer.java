package com.hermes.engine;

import com.hermes.domain.IntegrationEdge;
import com.hermes.domain.SystemNode;
import com.hermes.graph.IntegrationGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Orders the restart of an affected subgraph with Kahn's algorithm.
 *
 * <p>Kahn's over recursive DFS topo-sort for two reasons: it naturally emits
 * "waves" — sets of systems with no remaining upstream dependencies that can
 * be restored in parallel — and cycle detection falls out as a side effect:
 * when the ready-queue empties while nodes remain, those nodes sit on (or
 * behind) a cycle. Hermes then picks a deterministic <em>cycle break point</em>
 * (lowest remaining in-degree, then highest business criticality), flags it
 * for manual reconciliation, and continues the sort — so one cycle never
 * degrades the whole recovery plan. Within a wave, systems are ordered by
 * business criticality so the most revenue-relevant come back first.</p>
 */
public final class RecoverySequencer {

    private RecoverySequencer() {
    }

    /**
     * @param subgraphNodeIds the failed system plus everything it affected
     * @return ordered recovery steps; cycle members are flagged with
     *         {@code manualInterventionRequired} instead of failing the sort
     */
    public static List<RecoveryStep> sequence(IntegrationGraph graph, String failedSystemId,
                                              Collection<String> subgraphNodeIds) {
        Set<String> subgraph = new HashSet<>(subgraphNodeIds);
        subgraph.add(failedSystemId);

        // In-degree restricted to edges inside the affected subgraph
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Set<String>> upstreamOf = new HashMap<>();
        for (String id : subgraph) {
            inDegree.put(id, 0);
            upstreamOf.put(id, new TreeSet<>());
        }
        for (String id : subgraph) {
            for (IntegrationEdge edge : graph.outgoingEdges(id)) {
                if (subgraph.contains(edge.targetId())) {
                    inDegree.merge(edge.targetId(), 1, Integer::sum);
                    upstreamOf.get(edge.targetId()).add(id);
                }
            }
        }

        Comparator<String> byCriticalityDesc = Comparator
                .comparingInt((String id) -> graph.requireNode(id).getBusinessCriticality()).reversed()
                .thenComparing(Comparator.naturalOrder());

        List<RecoveryStep> steps = new ArrayList<>();
        Set<String> remaining = new HashSet<>(subgraph);
        Set<String> restored = new HashSet<>();
        int wave = 0;
        int stepNumber = 0;

        TreeSet<String> ready = new TreeSet<>(byCriticalityDesc);
        remaining.stream().filter(id -> inDegree.get(id) == 0).forEach(ready::add);

        while (!remaining.isEmpty()) {
            boolean cycleBreakWave = ready.isEmpty();
            if (cycleBreakWave) {
                ready.add(chooseBreakPoint(graph, failedSystemId, remaining, inDegree, byCriticalityDesc));
            }

            wave++;
            List<String> currentWave = new ArrayList<>(ready);
            ready.clear();
            for (String id : currentWave) {
                SystemNode node = graph.requireNode(id);
                stepNumber++;
                steps.add(buildStep(stepNumber, wave, node, cycleBreakWave,
                        upstreamOf.get(id), restored));
                remaining.remove(id);
                restored.add(id);
                for (IntegrationEdge edge : graph.outgoingEdges(id)) {
                    if (remaining.contains(edge.targetId())
                            && inDegree.merge(edge.targetId(), -1, Integer::sum) == 0) {
                        ready.add(edge.targetId());
                    }
                }
            }
        }

        return steps;
    }

    /**
     * Kahn's stalled: everything left sits on or behind a cycle. Prefer the
     * failed system itself as the break point (operations restores the dead
     * system first regardless of topology); otherwise pick an actual cycle
     * member — a node that can reach itself within the remaining set — with
     * the fewest unresolved upstream feeds, tie-broken by criticality.
     */
    private static String chooseBreakPoint(IntegrationGraph graph, String failedSystemId,
                                           Set<String> remaining, Map<String, Integer> inDegree,
                                           Comparator<String> byCriticalityDesc) {
        if (remaining.contains(failedSystemId)) {
            return failedSystemId;
        }
        Comparator<String> preference = Comparator
                .comparingInt((String id) -> inDegree.get(id)).thenComparing(byCriticalityDesc);
        return remaining.stream()
                .filter(id -> onCycleWithin(graph, id, remaining))
                .min(preference)
                .orElseGet(() -> remaining.stream().min(preference).orElseThrow());
    }

    /** True if {@code start} can reach itself using only nodes in {@code within}. */
    private static boolean onCycleWithin(IntegrationGraph graph, String start, Set<String> within) {
        Set<String> seen = new HashSet<>();
        List<String> frontier = List.of(start);
        while (!frontier.isEmpty()) {
            List<String> next = new ArrayList<>();
            for (String current : frontier) {
                for (IntegrationEdge edge : graph.outgoingEdges(current)) {
                    String target = edge.targetId();
                    if (target.equals(start)) {
                        return true;
                    }
                    if (within.contains(target) && seen.add(target)) {
                        next.add(target);
                    }
                }
            }
            frontier = next;
        }
        return false;
    }

    private static RecoveryStep buildStep(int stepNumber, int wave, SystemNode node,
                                          boolean cycleBreak, Set<String> upstream,
                                          Set<String> restored) {
        if (cycleBreak) {
            List<String> unresolved = upstream.stream().filter(u -> !restored.contains(u)).toList();
            return new RecoveryStep(stepNumber, wave, node.getId(), node.getName(),
                    "Restore " + node.getName() + " as cycle break point within a coordinated maintenance window",
                    "Part of a cyclic dependency — no valid topological order exists; unresolved upstream feed(s) "
                            + String.join(", ", unresolved)
                            + " must be reconciled manually after restoration",
                    true);
        }
        if (upstream.isEmpty()) {
            return new RecoveryStep(stepNumber, wave, node.getId(), node.getName(),
                    "Restore " + node.getName() + " and verify core connectivity",
                    "No upstream dependencies within the affected set — safe starting point",
                    false);
        }
        return new RecoveryStep(stepNumber, wave, node.getId(), node.getName(),
                "Restore " + node.getName() + ", then replay/verify inbound queues",
                "Upstream feed(s) " + String.join(", ", upstream) + " restored in earlier waves",
                false);
    }
}
