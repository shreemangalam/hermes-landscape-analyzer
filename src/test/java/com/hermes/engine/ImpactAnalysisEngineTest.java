package com.hermes.engine;

import com.hermes.domain.DegradationType;
import com.hermes.domain.IntegrationEdge;
import com.hermes.domain.Protocol;
import com.hermes.domain.SlaClass;
import com.hermes.domain.SystemNode;
import com.hermes.graph.IntegrationGraph;
import com.hermes.graph.NodeNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImpactAnalysisEngineTest {

    /**
     * Test landscape:
     *
     * <pre>
     *   ECC ──► SF ──► PORTAL
     *   ECC ──► MDM ──► SF
     *   ECC ──► FIN ◄── HR   (HR stays healthy)
     * </pre>
     */
    private IntegrationGraph graph;

    @BeforeEach
    void setUp() {
        graph = new IntegrationGraph();
        graph.addNode(node("ECC", 10));
        graph.addNode(node("SF", 9));
        graph.addNode(node("MDM", 8));
        graph.addNode(node("FIN", 9));
        graph.addNode(node("PORTAL", 8));
        graph.addNode(node("HR", 6));

        graph.addEdge(edge("E1", "ECC", "SF", 9, SlaClass.REAL_TIME));
        graph.addEdge(edge("E2", "ECC", "MDM", 8, SlaClass.NEAR_REAL_TIME));
        graph.addEdge(edge("E3", "MDM", "SF", 8, SlaClass.NEAR_REAL_TIME));
        graph.addEdge(edge("E4", "ECC", "FIN", 9, SlaClass.REAL_TIME));
        graph.addEdge(edge("E5", "HR", "FIN", 7, SlaClass.BATCH));
        graph.addEdge(edge("E6", "SF", "PORTAL", 7, SlaClass.REAL_TIME));
    }

    @Test
    void classifiesDirectAndCascadeByBfsRing() {
        ImpactReport report = ImpactAnalysisEngine.analyze(graph, "ECC", DegradationType.COMPLETE_OUTAGE);

        assertThat(find(report, "SF").impactType()).isEqualTo(ImpactType.DIRECT);
        assertThat(find(report, "SF").hopDistance()).isEqualTo(1);
        assertThat(find(report, "PORTAL").impactType()).isEqualTo(ImpactType.CASCADE);
        assertThat(find(report, "PORTAL").hopDistance()).isEqualTo(2);
    }

    @Test
    void distinguishesOrphanedFromPartiallySourcedConsumers() {
        ImpactReport report = ImpactAnalysisEngine.analyze(graph, "ECC", DegradationType.COMPLETE_OUTAGE);

        // SF's two feeds (ECC, MDM) are both gone -> data starvation
        assertThat(find(report, "SF").sourcingStatus()).isEqualTo(SourcingStatus.ORPHANED);
        // FIN still receives from healthy HR -> degraded, not dead
        assertThat(find(report, "FIN").sourcingStatus()).isEqualTo(SourcingStatus.PARTIAL);
    }

    @Test
    void healthyUpstreamSystemsAreNotAffected() {
        ImpactReport report = ImpactAnalysisEngine.analyze(graph, "ECC", DegradationType.COMPLETE_OUTAGE);

        assertThat(report.affectedSystems())
                .extracting(AffectedSystem::systemId)
                .containsExactlyInAnyOrder("SF", "MDM", "FIN", "PORTAL")
                .doesNotContain("HR", "ECC");
    }

    @Test
    void completeOutageOfCoreSystemIsP1() {
        ImpactReport report = ImpactAnalysisEngine.analyze(graph, "ECC", DegradationType.COMPLETE_OUTAGE);
        assertThat(report.priority()).isEqualTo(Priority.P1);
    }

    @Test
    void performanceDegradationScalesScoresDown() {
        ImpactReport outage = ImpactAnalysisEngine.analyze(graph, "ECC", DegradationType.COMPLETE_OUTAGE);
        ImpactReport slowdown = ImpactAnalysisEngine.analyze(graph, "ECC", DegradationType.PERFORMANCE_DEGRADATION);

        assertThat(find(slowdown, "SF").severityScore())
                .isLessThan(find(outage, "SF").severityScore());
        assertThat(slowdown.priority().compareTo(outage.priority())).isGreaterThanOrEqualTo(0);
    }

    @Test
    void affectedIntegrationsAreRankedByImpactScore() {
        ImpactReport report = ImpactAnalysisEngine.analyze(graph, "ECC", DegradationType.COMPLETE_OUTAGE);

        assertThat(report.affectedIntegrations()).hasSize(5); // every edge except the healthy HR feed
        assertThat(report.affectedIntegrations())
                .extracting(AffectedIntegration::integrationId)
                .doesNotContain("E5");
        assertThat(report.affectedIntegrations())
                .isSortedAccordingTo(Comparator.comparingDouble(AffectedIntegration::impactScore).reversed());
        assertThat(report.affectedIntegrations().get(0).rank()).isEqualTo(1);
    }

    @Test
    void statisticsAddUp() {
        ImpactReport report = ImpactAnalysisEngine.analyze(graph, "ECC", DegradationType.COMPLETE_OUTAGE);
        ImpactStatistics stats = report.statistics();

        assertThat(stats.totalAffectedSystems()).isEqualTo(4);
        assertThat(stats.directImpactCount() + stats.cascadeImpactCount())
                .isEqualTo(stats.totalAffectedSystems());
        assertThat(stats.orphanedConsumerCount() + stats.partiallyDegradedCount())
                .isEqualTo(stats.totalAffectedSystems());
        assertThat(stats.maxCascadeDepth()).isEqualTo(2);
    }

    @Test
    void reportsCycleWarningsOnlyWhenCycleTouchesAffectedSet() {
        // unrelated island with its own cycle
        graph.addNode(node("X", 4));
        graph.addNode(node("Y", 4));
        graph.addEdge(edge("EX", "X", "Y", 3, SlaClass.BATCH));
        graph.addEdge(edge("EY", "Y", "X", 3, SlaClass.BATCH));

        ImpactReport eccReport = ImpactAnalysisEngine.analyze(graph, "ECC", DegradationType.COMPLETE_OUTAGE);
        assertThat(eccReport.landscapeWarnings()).isEmpty();

        ImpactReport xReport = ImpactAnalysisEngine.analyze(graph, "X", DegradationType.COMPLETE_OUTAGE);
        assertThat(xReport.landscapeWarnings()).hasSize(1);
        assertThat(xReport.landscapeWarnings().get(0)).contains("Cycle detected");
    }

    @Test
    void traversalTerminatesOnCyclicLandscape() {
        graph.addEdge(edge("E7", "PORTAL", "ECC", 2, SlaClass.BATCH)); // close a loop back to the failure

        ImpactReport report = ImpactAnalysisEngine.analyze(graph, "ECC", DegradationType.COMPLETE_OUTAGE);
        assertThat(report.statistics().totalAffectedSystems()).isEqualTo(4);
        assertThat(report.landscapeWarnings()).anySatisfy(w -> assertThat(w).contains("Cycle detected"));
    }

    @Test
    void rejectsUnknownSystem() {
        assertThatThrownBy(() -> ImpactAnalysisEngine.analyze(graph, "NOPE", DegradationType.COMPLETE_OUTAGE))
                .isInstanceOf(NodeNotFoundException.class);
    }

    private static AffectedSystem find(ImpactReport report, String systemId) {
        return report.affectedSystems().stream()
                .filter(a -> a.systemId().equals(systemId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(systemId + " not in report"));
    }

    private static SystemNode node(String id, int criticality) {
        return new SystemNode(id, id, "TEST", criticality, null);
    }

    private static IntegrationEdge edge(String id, String source, String target, int criticality, SlaClass sla) {
        return new IntegrationEdge(id, id, source, target, Protocol.REST, "Payload", "Test", criticality, sla);
    }
}
