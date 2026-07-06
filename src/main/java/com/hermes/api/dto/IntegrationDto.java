package com.hermes.api.dto;

import com.hermes.domain.IntegrationEdge;
import com.hermes.domain.Protocol;
import com.hermes.domain.SlaClass;

/** One integration edge in the health map. */
public record IntegrationDto(
        String id,
        String name,
        String source,
        String target,
        Protocol protocol,
        String dataType,
        String businessProcess,
        int criticality,
        SlaClass slaClass) {

    public static IntegrationDto from(IntegrationEdge edge) {
        return new IntegrationDto(edge.id(), edge.name(), edge.sourceId(), edge.targetId(),
                edge.protocol(), edge.dataType(), edge.businessProcess(), edge.criticality(), edge.slaClass());
    }
}
