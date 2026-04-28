package com.logSystem.common.elasticsearch.dto;

import java.time.Instant;

/**
 * 1시간 단위 로그 발생 건수 데이터 포인트.
 *
 * <p>API·DB·Error 3개 인덱스의 date_histogram 집계 결과를 시간 기준으로 병합한 값이다.
 * 프론트엔드 Recharts 멀티라인 차트의 데이터 포인트로 직접 사용된다.
 *
 * @param hour       버킷 시작 시각 (해당 1시간 구간의 시작점, UTC)
 * @param apiCount   해당 구간의 API 로그 발생 건수
 * @param dbCount    해당 구간의 DB 로그 발생 건수
 * @param errorCount 해당 구간의 Error 로그 발생 건수
 */
public record HourlyLogTrendPoint(
    Instant hour,
    long    apiCount,
    long    dbCount,
    long    errorCount
) {}
