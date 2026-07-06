package com.hermes.engine;

import com.hermes.domain.IntegrationEdge;
import com.hermes.domain.Protocol;
import com.hermes.domain.SlaClass;
import com.hermes.domain.SystemNode;
import com.hermes.graph.IntegrationGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecoverySequencerTest {

    @Test
    void ordersLinearChainUpstreamFirst() {
        IntegrationGraph graph = graphOf(Set.of("A", "B", "C"));
        connect(graph, "A", "B");
        connect(graph, "B", "C");

        List<RecoveryStep> steps = RecoverySequencer.sequence(graph, "A", Set.of("A", "B", "C"));

        assertThat(steps).extracting(RecoveryStep::systemId).containsExactly("A", "B", "C");
        assertThat(steps).extracting(RecoveryStep::wave).containsExactly(1, 2, 3);
        assertThat(steps).noneMatch(RecoveryStep::manualInterventionRequired);
    }

    @Test
    void groupsIndependentSystemsIntoTheSameWave() {
        // diamond: A feeds B and C, both feed D
        IntegrationGraph graph = graphOf(Set.of("A", "B", "C", "D"));
        connect(graph, "A", "B");
        connect(graph, "A", "C");
        connect(graph, "B", "D");
        connect(graph, "C", "D");

        List<RecoveryStep> steps = RecoverySequencer.sequence(graph, "A", Set.of("A", "B", "C", "D"));

        assertThat(byId(steps, "B").wave()).isEqualTo(2);
        assertThat(byId(steps, "C").wave()).isEqualTo(2);
        assertThat(byId(steps, "D").wave()).isEqualTo(3);
    }

    @Test
    void restoresHigherCriticalitySystemsFirstWithinAWave() {
        IntegrationGraph graph = new IntegrationGraph();
        graph.addNode(node("A", 5));
        graph.addNode(node("LOW", 3));
        graph.addNode(node("HIGH", 9));
        connect(graph, "A", "LOW");
        connect(graph, "A", "HIGH");

        List<RecoveryStep> steps = RecoverySequencer.sequence(graph, "A", Set.of("A", "LOW", "HIGH"));

        assertThat(steps).extracting(RecoveryStep::systemId).containsExactly("A", "HIGH", "LOW");
    }

    @Test
    void breaksCycleAtFailedSystemAndContinues() {
        // A -> B -> C -> A: no valid topological order exists
        IntegrationGraph graph = graphOf(Set.of("A", "B", "C"));
        connect(graph, "A", "B");
        connect(graph, "B", "C");
        connect(graph, "C", "A");

        List<RecoveryStep> steps = RecoverySequencer.sequence(graph, "A", Set.of("A", "B", "C"));

        assertThat(steps).extracting(RecoveryStep::systemId).containsExactly("A", "B", "C");
        assertThat(byId(steps, "A").manualInterventionRequired()).isTrue();
        assertThat(byId(steps, "A").rationale()).contains("cyclic dependency");
        assertThat(byId(steps, "B").manualInterventionRequired()).isFalse();
        assertThat(byId(steps, "C").manualInterventionRequired()).isFalse();
    }

    @Test
    void flagsOnlyCycleMembersNotSystemsBehindTheCycle() {
        // ROOT feeds a two-node cycle, LEAF hangs off the cycle
        IntegrationGraph graph = new IntegrationGraph();
        graph.addNode(node("ROOT", 5));
        graph.addNode(node("A", 4));
        graph.addNode(node("B", 8));
        graph.addNode(node("LEAF", 6));
        connect(graph, "ROOT", "A");
        connect(graph, "A", "B");
        connect(graph, "B", "A");
        connect(graph, "B", "LEAF");

        List<RecoveryStep> steps = RecoverySequencer.sequence(graph, "ROOT",
                Set.of("ROOT", "A", "B", "LEAF"));

        List<RecoveryStep> manual = steps.stream().filter(RecoveryStep::manualInterventionRequired).toList();
        assertThat(manual).hasSize(1);
        // the break point must sit on the cycle, never be LEAF or ROOT
        assertThat(manual.get(0).systemId()).isIn("A", "B");
        assertThat(byId(steps, "LEAF").manualInterventionRequired()).isFalse();
        assertThat(byId(steps, "ROOT").wave()).isEqualTo(1);
    }

    @Test
    void handlesFailureWithNoDownstreamConsumers() {
        IntegrationGraph graph = graphOf(Set.of("LONELY"));

        List<RecoveryStep> steps = RecoverySequencer.sequence(graph, "LONELY", Set.of("LONELY"));

        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).wave()).isEqualTo(1);
    }

    private static RecoveryStep byId(List<RecoveryStep> steps, String systemId) {
        return steps.stream().filter(s -> s.systemId().equals(systemId)).findFirst().orElseThrow();
    }

    private static IntegrationGraph graphOf(Set<String> ids) {
        IntegrationGraph graph = new IntegrationGraph();
        ids.stream().sorted().forEach(id -> graph.addNode(node(id, 5)));
        return graph;
    }

    private static SystemNode node(String id, int criticality) {
        return new SystemNode(id, id, "TEST", criticality, null);
    }

    private static void connect(IntegrationGraph graph, String source, String target) {
        graph.addEdge(new IntegrationEdge("E-" + source + "-" + target, source + ">" + target,
                source, target, Protocol.REST, "Payload", "Test", 5, SlaClass.BATCH));
    }
}
