package com.hermes.engine;

import com.hermes.domain.Protocol;
import com.hermes.domain.SlaClass;

/** One integration (iFlow) impacted by the failure, ranked by impact score. */
public record AffectedIntegration(
        int rank,
        String integrationId,
        String name,
        String sourceId,
        String targetId,
        Protocol protocol,
        String businessProcess,
        SlaClass slaClass,
        double impactScore) {
}
