package com.hermes.service;

import com.hermes.domain.DegradationType;
import com.hermes.domain.NodeStatus;
import com.hermes.engine.AffectedSystem;
import com.hermes.engine.ImpactAnalysisEngine;
import com.hermes.engine.ImpactReport;
import com.hermes.engine.RecoveryStep;
import com.hermes.engine.SourcingStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs impact analyses and keeps the most recent reports retrievable by id.
 *
 * <p>An analysis also marks node statuses on the live health map (failed
 * system DOWN or DEGRADED depending on degradation type, orphaned consumers
 * DOWN, partially-sourced consumers DEGRADED) so that
 * {@code GET /landscape/health-map} reflects the incident.
 * {@code POST /landscape/reset} clears it.</p>
 */
@Service
public class AnalysisService {

    private static final int MAX_STORED_REPORTS = 100;

    private final LandscapeService landscapeService;

    // Bounded LRU of generated reports; access is synchronized on the map itself.
    private final Map<String, ImpactReport> reports = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ImpactReport> eldest) {
            return size() > MAX_STORED_REPORTS;
        }
    };

    public AnalysisService(LandscapeService landscapeService) {
        this.landscapeService = landscapeService;
    }

    public ImpactReport analyzeImpact(String systemId, DegradationType degradationType) {
        ImpactReport report = landscapeService.read(
                g -> ImpactAnalysisEngine.analyze(g, systemId, degradationType));

        landscapeService.write(g -> {
            g.requireNode(systemId).setStatus(
                    degradationType == DegradationType.COMPLETE_OUTAGE ? NodeStatus.DOWN : NodeStatus.DEGRADED);
            for (AffectedSystem affected : report.affectedSystems()) {
                g.requireNode(affected.systemId()).setStatus(
                        affected.sourcingStatus() == SourcingStatus.ORPHANED
                                ? NodeStatus.DOWN : NodeStatus.DEGRADED);
            }
            return null;
        });

        synchronized (reports) {
            reports.put(report.reportId(), report);
        }
        return report;
    }

    public ImpactReport getReport(String reportId) {
        ImpactReport report;
        synchronized (reports) {
            report = reports.get(reportId);
        }
        if (report == null) {
            throw new ReportNotFoundException("No impact report with id '" + reportId + "'");
        }
        return report;
    }

    /** Recovery plan for a hypothetical complete outage — read-only, no status changes. */
    public List<RecoveryStep> recoverySequence(String systemId) {
        return landscapeService.read(
                        g -> ImpactAnalysisEngine.analyze(g, systemId, DegradationType.COMPLETE_OUTAGE))
                .recommendedRecoverySequence();
    }
}
