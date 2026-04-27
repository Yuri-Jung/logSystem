package com.logSystem.analysis.controller;

import com.logSystem.analysis.dto.AnalysisResponseDto;
import com.logSystem.analysis.service.AnalysisService;
import com.logSystem.common.elasticsearch.dto.LogAggregationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 로그 분석 API 컨트롤러.
 *
 * @author Yuri-JUNG
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

  private final AnalysisService analysisService;

  @GetMapping
  public ResponseEntity<List<AnalysisResponseDto>> getAnalysis() {
    return ResponseEntity.ok(analysisService.getLogAnalysis());
  }

  /**
   * 최근 1시간 동안의 API 응답 시간 평균과 에러 발생 횟수를 반환한다.
   *
   * @return {@link LogAggregationResult} (avgApiResponseTimeMs, errorCount)
   */
  @GetMapping("/recent-stats")
  public ResponseEntity<LogAggregationResult> getRecentStats() {
    return ResponseEntity.ok(analysisService.getRecentStats());
  }
}
