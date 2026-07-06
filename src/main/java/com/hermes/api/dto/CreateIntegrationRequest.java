package com.hermes.api.dto;

import com.hermes.domain.Protocol;
import com.hermes.domain.SlaClass;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload for registering an integration edge (iFlow). Also the schema of {@code landscape.json} integrations. */
public record CreateIntegrationRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_\\-]{1,64}",
                message = "must be 1-64 chars of letters, digits, underscore or hyphen")
        String id,

        @NotBlank @Size(max = 120)
        String name,

        @NotBlank
        String source,

        @NotBlank
        String target,

        @NotNull
        Protocol protocol,

        @Size(max = 60)
        String dataType,

        @Size(max = 120)
        String businessProcess,

        @Min(1) @Max(10)
        int criticality,

        @NotNull
        SlaClass slaClass) {
}
