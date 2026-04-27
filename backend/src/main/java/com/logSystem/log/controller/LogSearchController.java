package com.logSystem.log.controller;

import com.logSystem.common.elasticsearch.dto.LogSearchCondition;
import com.logSystem.common.elasticsearch.dto.LogSearchResponse;
import com.logSystem.common.elasticsearch.service.LogSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Elasticsearch 로그 검색 API 컨트롤러.
 *
 * <p>엔드포인트:
 * <ul>
 *   <li>{@code GET /api/logs/trace/{traceId}} — traceId 기반 전체 흐름 조회
 *   <li>{@code GET /api/logs/search} — 조건 검색 (level·logType·시간 범위·페이지네이션)
 * </ul>
 *
 * @author Yuri-JUNG
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogSearchController {

  private final LogSearchService logSearchService;

  /**
   * traceId로 4개 인덱스 전체 요청 흐름을 타임스탬프 오름차순으로 반환한다.
   *
   * @param traceId 조회할 traceId
   * @return 요청 흐름에 속한 로그 목록 (최대 1,000건)
   */
  @GetMapping("/trace/{traceId}")
  public ResponseEntity<List<LogSearchResponse.LogSummaryDto>> getTrace(
      @PathVariable String traceId
  ) {
    return ResponseEntity.ok(logSearchService.findByTraceId(traceId));
  }

  /**
   * 조건 검색. 모든 파라미터는 optional이며, 생략하면 전체 대상이 된다.
   *
   * <p>SearchAfter 커서 페이지네이션: 이전 응답의 {@code nextCursor} 배열을
   * {@code searchAfter} 파라미터로 전달하면 다음 페이지를 가져온다.
   * searchAfter 없이 page/size만 지정하면 오프셋 기반 페이지네이션이 적용된다.
   *
   * @param level       INFO | WARN | ERROR
   * @param service     서비스명
   * @param logType     API | DB | EXTERNAL_API | ERROR
   * @param from        검색 시작 시각 (ISO-8601, e.g. 2025-01-01T00:00:00Z)
   * @param to          검색 종료 시각 (ISO-8601)
   * @param page        페이지 번호, 0-indexed (기본값 0)
   * @param size        페이지 크기 (기본값 20, 최대 100)
   * @param searchAfter SearchAfter 커서 값 목록 (오프셋 페이지네이션 사용 시 생략)
   * @return 검색 결과 및 다음 페이지 커서
   */
  @GetMapping("/search")
  public ResponseEntity<LogSearchResponse> search(
      @RequestParam(required = false) String  level,
      @RequestParam(required = false) String  service,
      @RequestParam(required = false) String  logType,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0")  int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) List<String> searchAfter
  ) {
    List<Object> cursor = (searchAfter != null) ? List.copyOf(searchAfter) : null;
    LogSearchCondition condition =
        new LogSearchCondition(null, level, service, logType, from, to, page, size, cursor);

    return ResponseEntity.ok(logSearchService.search(condition));
  }
}
