package com.hermes.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Payload for requesting a recovery sequence for a failed system. */
public record RecoveryRequest(@NotBlank String system) {
}
