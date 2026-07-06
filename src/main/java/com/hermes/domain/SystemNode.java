package com.hermes.domain;

import java.util.Objects;

/**
 * A node in the integration landscape: an SAP system, a SaaS application,
 * a legacy endpoint — anything that sends or receives data.
 *
 * <p>Identity, name, type and business criticality are immutable; only the
 * operational {@link NodeStatus} changes over the node's lifetime.</p>
 */
public class SystemNode {

    private final String id;
    private final String name;
    private final String type;
    private final int businessCriticality; // 1 (low) .. 10 (revenue-critical)
    private final String description;
    private volatile NodeStatus status;

    public SystemNode(String id, String name, String type, int businessCriticality, String description) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("System id must not be blank");
        }
        if (businessCriticality < 1 || businessCriticality > 10) {
            throw new IllegalArgumentException(
                    "businessCriticality must be between 1 and 10, got " + businessCriticality);
        }
        this.id = id;
        this.name = Objects.requireNonNullElse(name, id);
        this.type = Objects.requireNonNullElse(type, "UNKNOWN");
        this.businessCriticality = businessCriticality;
        this.description = description;
        this.status = NodeStatus.HEALTHY;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public int getBusinessCriticality() {
        return businessCriticality;
    }

    public String getDescription() {
        return description;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SystemNode other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id + " (" + type + ", criticality=" + businessCriticality + ")";
    }
}
