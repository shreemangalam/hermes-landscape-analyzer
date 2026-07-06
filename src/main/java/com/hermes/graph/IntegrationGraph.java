package com.hermes.graph;

import com.hermes.domain.IntegrationEdge;
import com.hermes.domain.SystemNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Directed graph of the integration landscape, implemented from scratch as an
 * adjacency list. Implemented by hand (rather than with a graph library) so the
 * traversal logic, edge metadata model and failure semantics stay fully under
 * our control — the impact engine needs both directions of adjacency and
 * metadata-aware traversal, which off-the-shelf libraries make awkward.
 *
 * <p>This class is not thread-safe on its own; {@code LandscapeService}
 * guards it with a read/write lock.</p>
 */
public class IntegrationGraph {

    private final Map<String, SystemNode> nodes = new HashMap<>();
    private final Map<String, List<IntegrationEdge>> outgoing = new HashMap<>();
    private final Map<String, List<IntegrationEdge>> incoming = new HashMap<>();
    private final Map<String, IntegrationEdge> edgesById = new HashMap<>();

    public void addNode(SystemNode node) {
        if (nodes.containsKey(node.getId())) {
            throw new DuplicateElementException("System '" + node.getId() + "' is already registered");
        }
        nodes.put(node.getId(), node);
        outgoing.put(node.getId(), new ArrayList<>());
        incoming.put(node.getId(), new ArrayList<>());
    }

    public void addEdge(IntegrationEdge edge) {
        if (edgesById.containsKey(edge.id())) {
            throw new DuplicateElementException("Integration '" + edge.id() + "' is already registered");
        }
        requireNode(edge.sourceId());
        requireNode(edge.targetId());
        edgesById.put(edge.id(), edge);
        outgoing.get(edge.sourceId()).add(edge);
        incoming.get(edge.targetId()).add(edge);
    }

    public SystemNode requireNode(String id) {
        SystemNode node = nodes.get(id);
        if (node == null) {
            throw new NodeNotFoundException("Unknown system '" + id + "'");
        }
        return node;
    }

    public Optional<SystemNode> findNode(String id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public boolean containsNode(String id) {
        return nodes.containsKey(id);
    }

    public List<IntegrationEdge> outgoingEdges(String nodeId) {
        return Collections.unmodifiableList(outgoing.getOrDefault(nodeId, List.of()));
    }

    public List<IntegrationEdge> incomingEdges(String nodeId) {
        return Collections.unmodifiableList(incoming.getOrDefault(nodeId, List.of()));
    }

    /** Number of distinct systems directly consuming data from this node. */
    public int fanOut(String nodeId) {
        return (int) outgoingEdges(nodeId).stream().map(IntegrationEdge::targetId).distinct().count();
    }

    /** Number of distinct systems directly feeding data into this node. */
    public int fanIn(String nodeId) {
        return (int) incomingEdges(nodeId).stream().map(IntegrationEdge::sourceId).distinct().count();
    }

    public boolean isLeaf(String nodeId) {
        return outgoingEdges(nodeId).isEmpty();
    }

    /** A junction both receives and forwards data — the amplifiers of any outage. */
    public boolean isJunction(String nodeId) {
        return fanIn(nodeId) >= 1 && fanOut(nodeId) >= 2;
    }

    /**
     * All systems transitively reachable from {@code nodeId} via outgoing
     * edges (excluding the start node). Breadth-first with a visited set, so
     * cycles are handled safely.
     */
    public Set<String> downstreamOf(String nodeId) {
        requireNode(nodeId);
        Set<String> visited = new HashSet<>();
        List<String> frontier = new ArrayList<>(List.of(nodeId));
        while (!frontier.isEmpty()) {
            List<String> next = new ArrayList<>();
            for (String current : frontier) {
                for (IntegrationEdge edge : outgoingEdges(current)) {
                    if (visited.add(edge.targetId())) {
                        next.add(edge.targetId());
                    }
                }
            }
            frontier = next;
        }
        visited.remove(nodeId);
        return visited;
    }

    public Collection<SystemNode> allNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public Collection<IntegrationEdge> allEdges() {
        return Collections.unmodifiableCollection(edgesById.values());
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edgesById.size();
    }
}
