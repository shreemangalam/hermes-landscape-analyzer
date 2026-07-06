package com.hermes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.api.dto.CreateIntegrationRequest;
import com.hermes.api.dto.CreateSystemRequest;
import com.hermes.api.dto.LandscapeWarning;
import com.hermes.service.LandscapeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Loads the landscape definition at startup. The bundled
 * {@code classpath:landscape.json} describes a realistic enterprise landscape;
 * point {@code hermes.landscape.path} at an external file to load your own.
 */
@Component
public class LandscapeLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LandscapeLoader.class);

    private final LandscapeService landscapeService;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final String landscapePath;

    public LandscapeLoader(LandscapeService landscapeService, ResourceLoader resourceLoader,
                           ObjectMapper objectMapper,
                           @Value("${hermes.landscape.path:classpath:landscape.json}") String landscapePath) {
        this.landscapeService = landscapeService;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.landscapePath = landscapePath;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Resource resource = resourceLoader.getResource(landscapePath);
        if (!resource.exists()) {
            log.warn("No landscape definition found at {} — starting with an empty landscape", landscapePath);
            return;
        }

        LandscapeFile landscape;
        try (InputStream in = resource.getInputStream()) {
            landscape = objectMapper.readValue(in, LandscapeFile.class);
        }

        for (CreateSystemRequest system : landscape.systems()) {
            landscapeService.registerSystem(system);
        }
        for (CreateIntegrationRequest integration : landscape.integrations()) {
            landscapeService.registerIntegration(integration);
        }

        log.info("Loaded landscape from {}: {} systems, {} integrations",
                landscapePath, landscape.systems().size(), landscape.integrations().size());
        for (LandscapeWarning warning : landscapeService.warnings()) {
            log.warn("Landscape warning [{}]: {}", warning.type(), warning.message());
        }
    }

    /** Schema of the landscape definition file. */
    public record LandscapeFile(
            List<CreateSystemRequest> systems,
            List<CreateIntegrationRequest> integrations) {

        public LandscapeFile {
            systems = systems == null ? List.of() : systems;
            integrations = integrations == null ? List.of() : integrations;
        }
    }
}
