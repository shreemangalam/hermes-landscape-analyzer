package com.hermes.api.dto;

import com.hermes.domain.DegradationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Payload for triggering an impact analysis. */
public record ImpactRequest(
        @NotBlank String system,
        @NotNull DegradationType degradationType) {
}
