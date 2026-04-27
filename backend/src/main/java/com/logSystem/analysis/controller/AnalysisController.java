package com.logSystem.analysis.controller;

import com.logSystem.analysis.dto.AnalysisResponseDto;
import com.logSystem.analysis.service.AnalysisService;
import com.logSystem.common.elasticsearch.dto.ErrorTrendPoint;
import com.logSystem.common.elasticsearch.dto.LogAggregationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  /**
   * 시간대별 에러 발생 추이를 반환한다.
   *
   * <p>프론트엔드 Recharts 차트 데이터 소스로 사용된다.
   *
   * @param hours 조회 시간 범위 (기본값 24, 최대 168)
   * @return 1시간 단위 {@link ErrorTrendPoint} 목록 (시간 오름차순)
   */
  @GetMapping("/error-trend")
  public ResponseEntity<List<ErrorTrendPoint>> getErrorTrend(
      @RequestParam(defaultValue = "24") int hours
  ) {
    return ResponseEntity.ok(analysisService.getErrorTrend(hours));
  }
}
