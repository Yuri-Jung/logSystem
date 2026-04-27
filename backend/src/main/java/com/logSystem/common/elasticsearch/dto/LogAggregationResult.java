package com.logSystem.common.elasticsearch.dto;

/**
 * 최근 1시간 로그 집계 결과.
 *
 * @param avgApiResponseTimeMs log-api 인덱스 durationMs 평균 (ms). 데이터 없으면 null
 * @param errorCount           log-error 인덱스 에러 발생 건수
 */
public record LogAggregationResult(
    Double avgApiResponseTimeMs,
    long   errorCount
) {}
