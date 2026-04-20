package com.logSystem.analysis.controller;

import com.logSystem.analysis.dto.AnalysisResponseDto;
import com.logSystem.analysis.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping
    public ResponseEntity<List<AnalysisResponseDto>> getAnalysis() {
        return ResponseEntity.ok(analysisService.getLogAnalysis());
    }
}
