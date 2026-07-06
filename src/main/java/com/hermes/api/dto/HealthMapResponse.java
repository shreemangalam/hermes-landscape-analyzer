package com.hermes.api.dto;

import java.util.List;

/** Full landscape snapshot: every system with its status, every integration. */
public record HealthMapResponse(
        int systemCount,
        int integrationCount,
        List<SystemHealthDto> systems,
        List<IntegrationDto> integrations) {
}
