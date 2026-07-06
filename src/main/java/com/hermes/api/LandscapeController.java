package com.hermes.api;

import com.hermes.api.dto.CreateIntegrationRequest;
import com.hermes.api.dto.CreateSystemRequest;
import com.hermes.api.dto.HealthMapResponse;
import com.hermes.api.dto.IntegrationDto;
import com.hermes.api.dto.LandscapeStatistics;
import com.hermes.api.dto.LandscapeWarning;
import com.hermes.domain.SystemNode;
import com.hermes.service.LandscapeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/landscape")
public class LandscapeController {

    private final LandscapeService landscapeService;

    public LandscapeController(LandscapeService landscapeService) {
        this.landscapeService = landscapeService;
    }

    @PostMapping("/systems")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> registerSystem(@Valid @RequestBody CreateSystemRequest request) {
        SystemNode node = landscapeService.registerSystem(request);
        return Map.of("id", node.getId(), "message", "System registered");
    }

    @PostMapping("/integrations")
    @ResponseStatus(HttpStatus.CREATED)
    public IntegrationDto registerIntegration(@Valid @RequestBody CreateIntegrationRequest request) {
        return IntegrationDto.from(landscapeService.registerIntegration(request));
    }

    @GetMapping("/health-map")
    public HealthMapResponse healthMap() {
        return landscapeService.healthMap();
    }

    @GetMapping("/warnings")
    public List<LandscapeWarning> warnings() {
        return landscapeService.warnings();
    }

    @GetMapping("/statistics")
    public LandscapeStatistics statistics() {
        return landscapeService.statistics();
    }

    @PostMapping("/reset")
    public Map<String, String> reset() {
        landscapeService.resetStatuses();
        return Map.of("message", "All system statuses reset to HEALTHY");
    }
}
