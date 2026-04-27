package com.logSystem.common.elasticsearch.dto;

import java.time.Instant;
import java.util.List;

/**
 * 로그 검색 조건.
 *
 * <p>모든 필드는 optional이다. null인 필드는 검색 조건에서 제외된다.
 *
 * @param traceId     요청 흐름 식별자 — 4개 인덱스 cross-index 조회 (null = 전체)
 * @param level       INFO | WARN | ERROR (null = 전체)
 * @param service     서비스명 정확 일치 필터 (null = 전체)
 * @param logType     API | DB | EXTERNAL_API | ERROR (null = 전체)
 * @param from        검색 시작 시각 (null = 제한 없음)
 * @param to          검색 종료 시각 (null = 제한 없음)
 * @param page        페이지 번호, 0-indexed (기본값 0)
 * @param size        페이지 크기 (기본값 20, 최대 100)
 * @param searchAfter SearchAfter 커서 값 목록 — 이전 응답의 {@code nextCursor} 값
 *                    (null이면 from/size 페이지네이션 사용)
 */
public record LogSearchCondition(
    String        traceId,
    String        level,
    String        service,
    String        logType,
    Instant       from,
    Instant       to,
    int           page,
    int           size,
    List<Object>  searchAfter
) {
  private static final int MAX_SIZE = 100;
  private static final int DEFAULT_SIZE = 20;

  public LogSearchCondition {
    if (size <= 0 || size > MAX_SIZE) size = DEFAULT_SIZE;
    if (page < 0)                     page = 0;
  }
}
