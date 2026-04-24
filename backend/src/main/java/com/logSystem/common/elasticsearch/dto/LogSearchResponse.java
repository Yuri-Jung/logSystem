package com.logSystem.common.elasticsearch.dto;

import java.time.Instant;
import java.util.List;

/**
 * 로그 검색 결과.
 *
 * @param items      현재 페이지의 로그 목록
 * @param totalHits  전체 검색 결과 수 (페이지네이션 UI에 활용)
 * @param page       현재 페이지 번호 (0-indexed)
 * @param size       페이지 크기
 * @param nextCursor SearchAfter 다음 페이지 커서 값 (마지막 항목의 정렬 기준값).
 *                   이 값을 다음 요청의 {@code searchAfter}로 전달하면 커서 기반 페이지네이션이 된다.
 *                   결과가 없거나 마지막 페이지이면 {@code null}
 */
public record LogSearchResponse(
    List<LogSummaryDto> items,
    long                totalHits,
    int                 page,
    int                 size,
    List<Object>        nextCursor
) {

  /**
   * 단일 로그 항목의 요약 정보.
   *
   * @param id          document ID ({@code {topic}-{partition}-{offset}})
   * @param logType     API | DB | EXTERNAL_API | ERROR
   * @param traceId     요청 흐름 전체 식별자
   * @param spanId      작업 단위 식별자
   * @param parentSpanId 부모 span 식별자
   * @param timestamp   로그 발생 시각
   * @param level       INFO | WARN | ERROR
   * @param service     서비스명
   * @param environment 실행 환경
   * @param host        호스트명
   * @param durationMs  처리 시간 (ms)
   */
  public record LogSummaryDto(
      String  id,
      String  logType,
      String  traceId,
      String  spanId,
      String  parentSpanId,
      Instant timestamp,
      String  level,
      String  service,
      String  environment,
      String  host,
      Long    durationMs
  ) {}
}
