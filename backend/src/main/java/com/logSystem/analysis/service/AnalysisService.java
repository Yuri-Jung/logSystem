package com.logSystem.analysis.service;

import com.logSystem.analysis.dto.AnalysisResponseDto;
import com.logSystem.common.elasticsearch.dto.ErrorTrendPoint;
import com.logSystem.common.elasticsearch.dto.LogAggregationResult;
import com.logSystem.common.elasticsearch.service.LogSearchService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 로그 분석 서비스.
 *
 * @author Yuri-JUNG
 */
@Service
public class AnalysisService {

  private final LogSearchService logSearchService;

  public AnalysisService(LogSearchService logSearchService) {
    this.logSearchService = logSearchService;
  }

  public List<AnalysisResponseDto> getLogAnalysis() {
    // TODO: MySQL 기반 분석 구현 필요
    return Collections.emptyList();
  }

  /**
   * 최근 1시간 동안의 API 응답 시간 평균과 에러 발생 횟수를 반환한다.
   *
   * @return 집계 결과 (avgApiResponseTimeMs, errorCount)
   */
  public LogAggregationResult getRecentStats() {
    return logSearchService.aggregateRecentStats();
  }

  /**
   * 최근 N시간 동안의 시간대별 에러 발생 추이를 반환한다.
   *
   * @param hours 조회 시간 범위 (기본값 24시간, 최대 168시간=7일)
   * @return 1시간 단위 에러 발생 건수 목록
   */
  public List<ErrorTrendPoint> getErrorTrend(int hours) {
    int clampedHours = Math.min(Math.max(hours, 1), 168);
    return logSearchService.getErrorTrend(clampedHours);
  }
}
