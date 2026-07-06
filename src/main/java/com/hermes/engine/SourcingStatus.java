package com.hermes.engine;

/** Whether an affected system still has any healthy data source left. */
public enum SourcingStatus {
    /** Every inbound integration originates from a failed or affected system: complete data starvation. */
    ORPHANED,
    /** At least one inbound integration still comes from a healthy system: degraded, not dead. */
    PARTIAL
}
