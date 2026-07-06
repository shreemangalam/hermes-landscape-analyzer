package com.hermes.graph;

import com.hermes.domain.IntegrationEdge;
import com.hermes.domain.Protocol;
import com.hermes.domain.SlaClass;
import com.hermes.domain.SystemNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CycleDetectorTest {

    @Test
    void findsNoCyclesInAcyclicGraph() {
        IntegrationGraph graph = graphOf("A", "B", "C", "D");
        connect(graph, "A", "B");
        connect(graph, "B", "C");
        connect(graph, "A", "D");

        assertThat(CycleDetector.findCycles(graph)).isEmpty();
    }

    @Test
    void findsSimpleThreeNodeCycle() {
        IntegrationGraph graph = graphOf("WMS", "ARCHIVE", "ERP");
        connect(graph, "WMS", "ARCHIVE");
        connect(graph, "ARCHIVE", "ERP");
        connect(graph, "ERP", "WMS");

        List<List<String>> cycles = CycleDetector.findCycles(graph);
        assertThat(cycles).hasSize(1);
        // canonical form starts at the lexicographically smallest node
        assertThat(cycles.get(0)).containsExactly("ARCHIVE", "ERP", "WMS");
    }

    @Test
    void findsCycleEvenWhenReachedThroughAcyclicPrefix() {
        IntegrationGraph graph = graphOf("ROOT", "A", "B", "C");
        connect(graph, "ROOT", "A");
        connect(graph, "A", "B");
        connect(graph, "B", "C");
        connect(graph, "C", "A");

        List<List<String>> cycles = CycleDetector.findCycles(graph);
        assertThat(cycles).hasSize(1);
        assertThat(cycles.get(0)).containsExactlyInAnyOrder("A", "B", "C");
    }

    @Test
    void findsTwoIndependentCycles() {
        IntegrationGraph graph = graphOf("A", "B", "X", "Y", "Z");
        connect(graph, "A", "B");
        connect(graph, "B", "A");
        connect(graph, "X", "Y");
        connect(graph, "Y", "Z");
        connect(graph, "Z", "X");

        assertThat(CycleDetector.findCycles(graph)).hasSize(2);
    }

    private static IntegrationGraph graphOf(String... ids) {
        IntegrationGraph graph = new IntegrationGraph();
        for (String id : ids) {
            graph.addNode(new SystemNode(id, id, "TEST", 5, null));
        }
        return graph;
    }

    private static void connect(IntegrationGraph graph, String source, String target) {
        graph.addEdge(new IntegrationEdge("E-" + source + "-" + target, source + ">" + target,
                source, target, Protocol.REST, "Payload", "Test", 5, SlaClass.BATCH));
    }
}
