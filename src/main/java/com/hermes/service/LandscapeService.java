package com.hermes.service;

import com.hermes.api.dto.CreateIntegrationRequest;
import com.hermes.api.dto.CreateSystemRequest;
import com.hermes.api.dto.HealthMapResponse;
import com.hermes.api.dto.IntegrationDto;
import com.hermes.api.dto.LandscapeStatistics;
import com.hermes.api.dto.LandscapeWarning;
import com.hermes.api.dto.SystemHealthDto;
import com.hermes.domain.IntegrationEdge;
import com.hermes.domain.NodeStatus;
import com.hermes.domain.SystemNode;
import com.hermes.graph.CycleDetector;
import com.hermes.graph.IntegrationGraph;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns the single in-memory landscape graph and guards it with a read/write
 * lock: many concurrent analyses may read the graph, registrations take the
 * write lock.
 */
@Service
public class LandscapeService {

    private final IntegrationGraph graph = new IntegrationGraph();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** Run a read-only computation against the graph under the read lock. */
    public <T> T read(Function<IntegrationGraph, T> query) {
        lock.readLock().lock();
        try {
            return query.apply(graph);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Run a mutation against the graph under the write lock. */
    public <T> T write(Function<IntegrationGraph, T> mutation) {
        lock.writeLock().lock();
        try {
            return mutation.apply(graph);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public SystemNode registerSystem(CreateSystemRequest request) {
        SystemNode node = new SystemNode(request.id(), request.name(), request.type(),
                request.businessCriticality(), request.description());
        return write(g -> {
            g.addNode(node);
            return node;
        });
    }

    public IntegrationEdge registerIntegration(CreateIntegrationRequest request) {
        IntegrationEdge edge = new IntegrationEdge(request.id(), request.name(), request.source(),
                request.target(), request.protocol(), request.dataType(), request.businessProcess(),
                request.criticality(), request.slaClass());
        return write(g -> {
            g.addEdge(edge);
            return edge;
        });
    }

    public HealthMapResponse healthMap() {
        return read(g -> new HealthMapResponse(
                g.nodeCount(),
                g.edgeCount(),
                g.allNodes().stream()
                        .sorted(Comparator.comparing(SystemNode::getId))
                        .map(n -> new SystemHealthDto(n.getId(), n.getName(), n.getType(),
                                n.getBusinessCriticality(), n.getStatus(),
                                g.fanIn(n.getId()), g.fanOut(n.getId()),
                                g.isJunction(n.getId()), g.isLeaf(n.getId())))
                        .toList(),
                g.allEdges().stream()
                        .sorted(Comparator.comparing(IntegrationEdge::id))
                        .map(IntegrationDto::from)
                        .toList()));
    }

    public List<LandscapeWarning> warnings() {
        return read(g -> {
            List<LandscapeWarning> warnings = new ArrayList<>();
            for (List<String> cycle : CycleDetector.findCycles(g)) {
                warnings.add(new LandscapeWarning(LandscapeWarning.WarningType.CYCLE,
                        "Cycle detected: " + String.join(" -> ", cycle) + " -> " + cycle.get(0)));
            }
            for (SystemNode node : g.allNodes().stream()
                    .sorted(Comparator.comparing(SystemNode::getId)).toList()) {
                int fanIn = g.fanIn(node.getId());
                int fanOut = g.fanOut(node.getId());
                if (fanIn == 0 && fanOut == 0) {
                    warnings.add(new LandscapeWarning(LandscapeWarning.WarningType.ISOLATED_SYSTEM,
                            node.getId() + " has no integrations — possibly stale configuration"));
                } else if (fanIn == 1 && fanOut == 0 && node.getBusinessCriticality() >= 7) {
                    String onlySource = g.incomingEdges(node.getId()).get(0).sourceId();
                    warnings.add(new LandscapeWarning(LandscapeWarning.WarningType.SINGLE_SOURCE_CONSUMER,
                            node.getId() + " (criticality " + node.getBusinessCriticality()
                                    + ") depends on a single source " + onlySource
                                    + " — an outage there means complete data starvation"));
                }
            }
            return warnings;
        });
    }

    public LandscapeStatistics statistics() {
        return read(g -> {
            Map<String, Long> protocols = g.allEdges().stream().collect(Collectors.groupingBy(
                    e -> e.protocol().name(), Collectors.counting()));
            Map<String, Long> slas = g.allEdges().stream().collect(Collectors.groupingBy(
                    e -> e.slaClass().name(), Collectors.counting()));
            double avgFanOut = g.allNodes().stream()
                    .mapToInt(n -> g.fanOut(n.getId())).average().orElse(0.0);
            int junctions = (int) g.allNodes().stream().filter(n -> g.isJunction(n.getId())).count();
            int leaves = (int) g.allNodes().stream().filter(n -> g.isLeaf(n.getId())).count();
            List<LandscapeStatistics.CriticalSystem> top = g.allNodes().stream()
                    .map(n -> new LandscapeStatistics.CriticalSystem(n.getId(), n.getName(),
                            n.getBusinessCriticality(), g.fanOut(n.getId()),
                            n.getBusinessCriticality() + g.fanOut(n.getId()) * 1.5))
                    .sorted(Comparator.comparingDouble(LandscapeStatistics.CriticalSystem::score).reversed())
                    .limit(5)
                    .toList();
            return new LandscapeStatistics(g.nodeCount(), g.edgeCount(), protocols, slas,
                    Math.round(avgFanOut * 100.0) / 100.0, junctions, leaves,
                    CycleDetector.findCycles(g).size(), top);
        });
    }

    /** Restore every system to HEALTHY (clears the effects of previous analyses). */
    public void resetStatuses() {
        write(g -> {
            g.allNodes().forEach(n -> n.setStatus(NodeStatus.HEALTHY));
            return null;
        });
    }
}
