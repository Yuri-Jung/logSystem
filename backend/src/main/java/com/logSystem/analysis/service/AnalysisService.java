package com.logSystem.analysis.service;

import com.logSystem.analysis.dto.AnalysisResponseDto;
import com.logSystem.common.elasticsearch.dto.ErrorTrendPoint;
import com.logSystem.common.elasticsearch.dto.ExceptionRankItem;
import com.logSystem.common.elasticsearch.dto.HourlyLogTrendPoint;
import com.logSystem.common.elasticsearch.dto.LogAggregationResult;
import com.logSystem.common.elasticsearch.service.LogSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

  private final LogSearchService logSearchService;

  public AnalysisService(LogSearchService logSearchService) {
    this.logSearchService = logSearchService;
  }

  public List<AnalysisResponseDto> getLogAnalysis() {
    // TODO: MySQL 기반 분석 구현 필요
    return Collections.emptyList();
  }

  /**
   * 최근 1시간 동안의 API 응답 시간 평균·P95와 에러 발생 횟수를 반환한다.
   *
   * <p>ES 연결 실패 시 null/0 기본값으로 빈 결과를 반환하며 500을 발생시키지 않는다.
   *
   * @return 집계 결과 (avgApiResponseTimeMs, p95ApiResponseTimeMs, errorCount)
   */
  public LogAggregationResult getRecentStats() {
    try {
      return logSearchService.aggregateRecentStats();
    } catch (Exception e) {
      log.warn("ES 통계 집계 실패 (ES 미기동 상태일 수 있음): {}", e.getMessage());
      return new LogAggregationResult(null, null, 0L);
    }
  }

  /**
   * 최근 N시간 동안의 시간대별 에러 발생 추이를 반환한다.
   *
   * <p>ES 연결 실패 시 빈 목록을 반환하며 500을 발생시키지 않는다.
   *
   * @param hours 조회 시간 범위 (기본값 24시간, 최대 168시간=7일)
   * @return 1시간 단위 에러 발생 건수 목록
   */
  public List<ErrorTrendPoint> getErrorTrend(int hours) {
    try {
      int clampedHours = Math.min(Math.max(hours, 1), 168);
      return logSearchService.getErrorTrend(clampedHours);
    } catch (Exception e) {
      log.warn("ES 에러 추이 집계 실패: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * 최근 N시간 동안의 시간대별 API·DB·Error 로그 발생 추이를 반환한다.
   *
   * <p>ES 연결 실패 시 빈 목록을 반환하며 500을 발생시키지 않는다.
   *
   * @param hours 조회 시간 범위 (기본값 24시간, 최대 168시간)
   * @return 1시간 단위 {@link HourlyLogTrendPoint} 목록 (시간 오름차순)
   */
  public List<HourlyLogTrendPoint> getHourlyLogTrend(int hours) {
    try {
      int clampedHours = Math.min(Math.max(hours, 1), 168);
      return logSearchService.getHourlyLogTrend(clampedHours);
    } catch (Exception e) {
      log.warn("ES 시간별 추이 집계 실패: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * 발생 빈도 상위 N개 예외 클래스 통계를 반환한다.
   *
   * <p>ES 연결 실패 시 빈 목록을 반환하며 500을 발생시키지 않는다.
   *
   * @param limit 조회할 최대 항목 수 (기본값 5)
   * @return 발생 건수 내림차순 {@link ExceptionRankItem} 목록
   */
  public List<ExceptionRankItem> getTopExceptions(int limit) {
    try {
      int clampedLimit = Math.min(Math.max(limit, 1), 20);
      return logSearchService.getTopExceptions(clampedLimit);
    } catch (Exception e) {
      log.warn("ES TOP 예외 집계 실패: {}", e.getMessage());
      return List.of();
    }
  }
}
