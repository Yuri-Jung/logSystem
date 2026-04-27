package com.logSystem.common.elasticsearch.dto;

import java.time.Instant;

/**
 * 시간대별 에러 발생 건수 데이터 포인트.
 *
 * <p>Elasticsearch date_histogram 집계 결과를 1시간 단위 버킷으로 변환한 값이다.
 * 프론트엔드 Recharts 차트의 데이터 포인트로 직접 사용된다.
 *
 * @param hour       버킷 시작 시각 (해당 1시간 구간의 시작점)
 * @param errorCount 해당 1시간 내 에러 로그 발생 건수
 */
public record ErrorTrendPoint(
    Instant hour,
    long    errorCount
) {}
