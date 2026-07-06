package com.hermes.graph;

import com.hermes.domain.IntegrationEdge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects cycles in the landscape graph with an iterative three-color DFS.
 *
 * <p>Real landscapes do contain cycles — a callback iFlow that re-triggers the
 * original flow, or a legacy sync loop nobody remembers building. Hermes never
 * treats a cycle as an error: it reports each one as a landscape warning and
 * every traversal elsewhere is cycle-safe via visited sets.</p>
 *
 * <p>DFS reports each cycle found through a back edge (one cycle per back
 * edge); it is not an exhaustive enumeration of every elementary cycle, which
 * would be Johnson's algorithm and overkill for a warning report.</p>
 */
public final class CycleDetector {

    private enum Color { WHITE, GRAY, BLACK }

    private CycleDetector() {
    }

    /**
     * @return each detected cycle as the list of node ids along it, e.g.
     *         {@code [LEGACY_WMS, ARCHIVE_SYSTEM, LEGACY_ERP]}, canonicalized
     *         so the lexicographically smallest node comes first.
     */
    public static List<List<String>> findCycles(IntegrationGraph graph) {
        Map<String, Color> color = new HashMap<>();
        for (var node : graph.allNodes()) {
            color.put(node.getId(), Color.WHITE);
        }

        Set<List<String>> cycles = new LinkedHashSet<>();
        for (var node : graph.allNodes()) {
            if (color.get(node.getId()) == Color.WHITE) {
                dfs(graph, node.getId(), color, cycles);
            }
        }
        return new ArrayList<>(cycles);
    }

    private static void dfs(IntegrationGraph graph, String start,
                            Map<String, Color> color, Set<List<String>> cycles) {
        // Iterative DFS: each frame keeps its own edge iterator so we can
        // resume a node after exploring one of its children.
        Deque<Frame> stack = new ArrayDeque<>();
        List<String> path = new ArrayList<>();
        stack.push(new Frame(start, graph.outgoingEdges(start).iterator()));
        color.put(start, Color.GRAY);
        path.add(start);

        while (!stack.isEmpty()) {
            Frame frame = stack.peek();
            if (frame.edges.hasNext()) {
                String next = frame.edges.next().targetId();
                switch (color.get(next)) {
                    case WHITE -> {
                        color.put(next, Color.GRAY);
                        path.add(next);
                        stack.push(new Frame(next, graph.outgoingEdges(next).iterator()));
                    }
                    case GRAY -> cycles.add(canonicalize(path.subList(path.indexOf(next), path.size())));
                    case BLACK -> { /* already fully explored */ }
                }
            } else {
                color.put(frame.nodeId, Color.BLACK);
                path.remove(path.size() - 1);
                stack.pop();
            }
        }
    }

    /** Rotate the cycle so the smallest node id comes first, making cycles comparable. */
    private static List<String> canonicalize(List<String> cycle) {
        List<String> copy = new ArrayList<>(cycle);
        int minIndex = copy.indexOf(Collections.min(copy));
        Collections.rotate(copy, -minIndex);
        return List.copyOf(copy);
    }

    private record Frame(String nodeId, java.util.Iterator<IntegrationEdge> edges) {
    }
}
