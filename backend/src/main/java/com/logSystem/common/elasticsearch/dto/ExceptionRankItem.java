package com.logSystem.common.elasticsearch.dto;

/**
 * 예외 클래스별 발생 건수 랭킹 항목.
 *
 * <p>log-error 인덱스의 {@code exceptionClass} 필드 terms 집계 결과를 담는다.
 * 프론트엔드 Recharts 수평 바 차트의 데이터 포인트로 직접 사용된다.
 *
 * @param exceptionClass 예외 클래스 전체 경로 (e.g. java.lang.NullPointerException)
 * @param count          발생 건수
 */
public record ExceptionRankItem(
    String exceptionClass,
    long   count
) {}
