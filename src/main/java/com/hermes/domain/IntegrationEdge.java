package com.hermes.domain;

/**
 * A directed edge in the landscape graph: one iFlow (or point-to-point
 * interface) moving data from {@code sourceId} to {@code targetId}.
 */
public record IntegrationEdge(
        String id,
        String name,
        String sourceId,
        String targetId,
        Protocol protocol,
        String dataType,
        String businessProcess,
        int criticality, // 1 (informational) .. 10 (revenue-blocking)
        SlaClass slaClass) {

    public IntegrationEdge {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Integration id must not be blank");
        }
        if (sourceId == null || sourceId.isBlank() || targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("Integration " + id + " must have a source and a target");
        }
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Integration " + id + " must not be a self-loop");
        }
        if (criticality < 1 || criticality > 10) {
            throw new IllegalArgumentException(
                    "criticality must be between 1 and 10, got " + criticality + " for integration " + id);
        }
        if (protocol == null) {
            protocol = Protocol.REST;
        }
        if (slaClass == null) {
            slaClass = SlaClass.BATCH;
        }
    }
}
