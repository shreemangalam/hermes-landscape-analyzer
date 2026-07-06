package com.hermes.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload for registering a system node. Also the schema of {@code landscape.json} systems. */
public record CreateSystemRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_\\-]{1,64}",
                message = "must be 1-64 chars of letters, digits, underscore or hyphen")
        String id,

        @NotBlank @Size(max = 120)
        String name,

        @Size(max = 60)
        String type,

        @Min(1) @Max(10)
        int businessCriticality,

        @Size(max = 500)
        String description) {
}
