package com.hermes.engine;

/**
 * One step of the recommended recovery sequence. Steps in the same wave have
 * no dependencies on each other and can be restored in parallel.
 */
public record RecoveryStep(
        int step,
        int wave,
        String systemId,
        String systemName,
        String action,
        String rationale,
        boolean manualInterventionRequired) {
}
