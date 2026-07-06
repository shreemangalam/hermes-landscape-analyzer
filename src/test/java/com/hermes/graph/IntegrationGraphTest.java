package com.hermes.graph;

import com.hermes.domain.IntegrationEdge;
import com.hermes.domain.Protocol;
import com.hermes.domain.SlaClass;
import com.hermes.domain.SystemNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationGraphTest {

    private IntegrationGraph graph;

    @BeforeEach
    void setUp() {
        graph = new IntegrationGraph();
        graph.addNode(node("A", 9));
        graph.addNode(node("B", 5));
        graph.addNode(node("C", 7));
        graph.addNode(node("D", 3));
    }

    @Test
    void tracksOutgoingAndIncomingAdjacency() {
        graph.addEdge(edge("E1", "A", "B"));
        graph.addEdge(edge("E2", "A", "C"));
        graph.addEdge(edge("E3", "B", "C"));

        assertThat(graph.outgoingEdges("A")).extracting(IntegrationEdge::id).containsExactly("E1", "E2");
        assertThat(graph.incomingEdges("C")).extracting(IntegrationEdge::id).containsExactly("E2", "E3");
        assertThat(graph.fanOut("A")).isEqualTo(2);
        assertThat(graph.fanIn("C")).isEqualTo(2);
    }

    @Test
    void fanCountsAreDistinctBySystemNotByEdge() {
        // Two parallel iFlows between the same pair count as one consumer
        graph.addEdge(edge("E1", "A", "B"));
        graph.addEdge(edge("E2", "A", "B"));

        assertThat(graph.fanOut("A")).isEqualTo(1);
        assertThat(graph.fanIn("B")).isEqualTo(1);
        assertThat(graph.outgoingEdges("A")).hasSize(2);
    }

    @Test
    void rejectsDuplicateSystemIds() {
        assertThatThrownBy(() -> graph.addNode(node("A", 1)))
                .isInstanceOf(DuplicateElementException.class)
                .hasMessageContaining("A");
    }

    @Test
    void rejectsDuplicateIntegrationIds() {
        graph.addEdge(edge("E1", "A", "B"));
        assertThatThrownBy(() -> graph.addEdge(edge("E1", "B", "C")))
                .isInstanceOf(DuplicateElementException.class);
    }

    @Test
    void rejectsEdgesToUnknownSystems() {
        assertThatThrownBy(() -> graph.addEdge(edge("E1", "A", "NOPE")))
                .isInstanceOf(NodeNotFoundException.class)
                .hasMessageContaining("NOPE");
        // failed registration must not leave a dangling half-edge
        assertThat(graph.outgoingEdges("A")).isEmpty();
    }

    @Test
    void classifiesJunctionsAndLeaves() {
        graph.addEdge(edge("E1", "A", "B"));
        graph.addEdge(edge("E2", "B", "C"));
        graph.addEdge(edge("E3", "B", "D"));

        assertThat(graph.isJunction("B")).isTrue();
        assertThat(graph.isLeaf("C")).isTrue();
        assertThat(graph.isJunction("A")).isFalse();
    }

    @Test
    void downstreamTraversalSurvivesCycles() {
        graph.addEdge(edge("E1", "A", "B"));
        graph.addEdge(edge("E2", "B", "C"));
        graph.addEdge(edge("E3", "C", "A")); // cycle back to the start

        assertThat(graph.downstreamOf("A")).containsExactlyInAnyOrder("B", "C");
        assertThat(graph.downstreamOf("B")).containsExactlyInAnyOrder("A", "C");
    }

    private static SystemNode node(String id, int criticality) {
        return new SystemNode(id, id, "TEST", criticality, null);
    }

    private static IntegrationEdge edge(String id, String source, String target) {
        return new IntegrationEdge(id, id, source, target, Protocol.REST, "Payload", "Test", 5, SlaClass.BATCH);
    }
}
