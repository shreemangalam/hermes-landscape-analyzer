package com.hermes.api;

import com.hermes.api.dto.ImpactRequest;
import com.hermes.api.dto.RecoveryRequest;
import com.hermes.engine.ImpactReport;
import com.hermes.engine.RecoveryStep;
import com.hermes.service.AnalysisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/impact")
    public ImpactReport analyzeImpact(@Valid @RequestBody ImpactRequest request) {
        return analysisService.analyzeImpact(request.system(), request.degradationType());
    }

    @GetMapping("/impact/{reportId}")
    public ImpactReport getReport(@PathVariable String reportId) {
        return analysisService.getReport(reportId);
    }

    @PostMapping("/recovery-sequence")
    public List<RecoveryStep> recoverySequence(@Valid @RequestBody RecoveryRequest request) {
        return analysisService.recoverySequence(request.system());
    }
}
