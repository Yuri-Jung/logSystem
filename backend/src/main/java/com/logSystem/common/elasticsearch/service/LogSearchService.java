package com.logSystem.common.elasticsearch.service;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.logSystem.common.elasticsearch.document.ApiLogDocument;
import com.logSystem.common.elasticsearch.document.BaseDocument;
import com.logSystem.common.elasticsearch.document.DbLogDocument;
import com.logSystem.common.elasticsearch.document.ErrorLogDocument;
import com.logSystem.common.elasticsearch.document.ExternalApiLogDocument;
import com.logSystem.common.elasticsearch.dto.LogAggregationResult;
import com.logSystem.common.elasticsearch.dto.LogSearchCondition;
import com.logSystem.common.elasticsearch.dto.LogSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Elasticsearch 로그 검색·집계 서비스.
 *
 * <p>제공 기능:
 * <ul>
 *   <li>traceId 기반 4개 인덱스 전체 흐름 조회 (개별 조회 후 타임스탬프 기준 병합)
 *   <li>level·logType·시간 범위 조건 검색 + SearchAfter/from-size 페이지네이션
 *   <li>최근 1시간 API 응답 시간 평균 및 에러 발생 횟수 집계
 * </ul>
 *
 * @author Yuri-JUNG
 */
@Service
public class LogSearchService {

  private static final Logger log = LoggerFactory.getLogger(LogSearchService.class);

  private final ElasticsearchOperations esOperations;

  public LogSearchService(ElasticsearchOperations esOperations) {
    this.esOperations = esOperations;
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 조회
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * traceId로 4개 인덱스 전체 요청 흐름을 타임스탬프 오름차순으로 반환한다.
   *
   * <p>log-api, log-db, log-external, log-error 인덱스를 각각 조회하고
   * 결과를 타임스탬프 기준으로 병합 정렬한다. 단일 traceId에 최대 1,000건을 가정한다.
   *
   * @param traceId 조회할 traceId
   * @return 타임스탬프 오름차순으로 정렬된 로그 목록
   */
  public List<LogSearchResponse.LogSummaryDto> findByTraceId(String traceId) {
    NativeQuery query = NativeQuery.builder()
        .withQuery(q -> q.term(t -> t.field("traceId").value(traceId)))
        .withSort(s -> s.field(f -> f.field("timestamp").order(SortOrder.Asc)))
        .withMaxResults(1000)
        .build();

    List<LogSearchResponse.LogSummaryDto> merged = new ArrayList<>();
    merged.addAll(searchIndex(query, ApiLogDocument.class));
    merged.addAll(searchIndex(query, DbLogDocument.class));
    merged.addAll(searchIndex(query, ExternalApiLogDocument.class));
    merged.addAll(searchIndex(query, ErrorLogDocument.class));

    merged.sort(Comparator.comparing(LogSearchResponse.LogSummaryDto::timestamp));
    return merged;
  }

  /**
   * 조건 검색 + 페이지네이션.
   *
   * <p>{@link LogSearchCondition#logType()}이 지정된 경우 해당 인덱스만 조회한다.
   * logType이 null이면 log-api 인덱스를 기본으로 조회한다.
   *
   * <p>{@link LogSearchCondition#searchAfter()}가 있으면 SearchAfter 커서 방식을,
   * 없으면 from·size 오프셋 방식을 사용한다.
   *
   * @param condition 검색 조건 및 페이지네이션 파라미터
   * @return 검색 결과 (items, totalHits, nextCursor 포함)
   */
  public LogSearchResponse search(LogSearchCondition condition) {
    NativeQueryBuilder builder = NativeQuery.builder()
        .withQuery(buildBoolQuery(condition))
        .withSort(s -> s.field(f -> f.field("timestamp").order(SortOrder.Desc)));

    if (condition.searchAfter() != null && !condition.searchAfter().isEmpty()) {
      // SearchAfter 커서 방식: 대량 데이터 딥 페이징에 적합
      builder.withMaxResults(condition.size())
             .withSearchAfter(condition.searchAfter());
    } else {
      builder.withPageable(PageRequest.of(condition.page(), condition.size()));
    }

    NativeQuery query = builder.build();
    return searchByLogType(query, condition);
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 집계
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * 최근 1시간 동안의 API 응답 시간 평균과 에러 발생 횟수를 집계한다.
   *
   * <p>API 응답 시간 평균: log-api 인덱스의 durationMs avg 집계.
   * 에러 발생 횟수: log-error 인덱스의 totalHits.
   *
   * @return 집계 결과 (avgApiResponseTimeMs, errorCount)
   */
  public LogAggregationResult aggregateRecentStats() {
    String fromStr = Instant.now().minus(1, ChronoUnit.HOURS).toString();

    Double avgMs      = aggregateAvgApiResponseTime(fromStr);
    long   errorCount = countErrors(fromStr);

    return new LogAggregationResult(avgMs, errorCount);
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 내부 헬퍼
  // ──────────────────────────────────────────────────────────────────────────

  private Double aggregateAvgApiResponseTime(String fromStr) {
    NativeQuery query = NativeQuery.builder()
        .withQuery(q -> q.range(r -> r.date(d -> d.field("timestamp").gte(fromStr))))
        .withAggregation("avgResponseTime",
            Aggregation.of(a -> a.avg(avg -> avg.field("durationMs"))))
        .withMaxResults(0)
        .build();

    SearchHits<ApiLogDocument> hits = esOperations.search(query, ApiLogDocument.class);
    return extractAvgValue(hits, "avgResponseTime");
  }

  private long countErrors(String fromStr) {
    NativeQuery query = NativeQuery.builder()
        .withQuery(q -> q.range(r -> r.date(d -> d.field("timestamp").gte(fromStr))))
        .withMaxResults(0)
        .build();

    return esOperations.search(query, ErrorLogDocument.class).getTotalHits();
  }

  /** logType에 따라 대상 인덱스를 결정하여 검색한다. */
  private LogSearchResponse searchByLogType(NativeQuery query, LogSearchCondition condition) {
    String logType = condition.logType();

    if ("DB".equals(logType)) {
      return toResponse(esOperations.search(query, DbLogDocument.class), condition);
    } else if ("EXTERNAL_API".equals(logType)) {
      return toResponse(esOperations.search(query, ExternalApiLogDocument.class), condition);
    } else if ("ERROR".equals(logType)) {
      return toResponse(esOperations.search(query, ErrorLogDocument.class), condition);
    } else {
      // API 또는 logType=null → log-api 기본
      return toResponse(esOperations.search(query, ApiLogDocument.class), condition);
    }
  }

  /** SearchHits를 LogSearchResponse로 변환한다. */
  private <T extends BaseDocument> LogSearchResponse toResponse(
      SearchHits<T> hits, LogSearchCondition condition
  ) {
    List<LogSearchResponse.LogSummaryDto> items = hits.getSearchHits().stream()
        .map(SearchHit::getContent)
        .map(this::toSummaryDto)
        .toList();

    return new LogSearchResponse(
        items, hits.getTotalHits(), condition.page(), condition.size(), extractNextCursor(hits));
  }

  /** 단일 인덱스에서 조회하고 LogSummaryDto 목록으로 변환한다. */
  private <T extends BaseDocument> List<LogSearchResponse.LogSummaryDto> searchIndex(
      NativeQuery query, Class<T> clazz
  ) {
    return esOperations.search(query, clazz).getSearchHits().stream()
        .map(SearchHit::getContent)
        .map(this::toSummaryDto)
        .toList();
  }

  /** 검색 조건에서 bool 쿼리를 생성한다. null 필드는 조건에서 제외된다. */
  private Query buildBoolQuery(LogSearchCondition condition) {
    BoolQuery.Builder bool = new BoolQuery.Builder();

    if (condition.traceId() != null) {
      bool.must(m -> m.term(t -> t.field("traceId").value(condition.traceId())));
    }
    if (condition.level() != null) {
      bool.must(m -> m.term(t -> t.field("level").value(condition.level())));
    }
    if (condition.service() != null) {
      bool.must(m -> m.term(t -> t.field("service").value(condition.service())));
    }
    if (condition.logType() != null) {
      bool.must(m -> m.term(t -> t.field("logType").value(condition.logType())));
    }
    if (condition.from() != null || condition.to() != null) {
      bool.must(m -> m.range(r -> r.date(d -> {
        d.field("timestamp");
        if (condition.from() != null) d.gte(condition.from().toString());
        if (condition.to() != null)   d.lte(condition.to().toString());
        return d;
      })));
    }

    return bool.build()._toQuery();
  }

  private LogSearchResponse.LogSummaryDto toSummaryDto(BaseDocument doc) {
    return new LogSearchResponse.LogSummaryDto(
        doc.getId(),
        doc.getLogType(),
        doc.getTraceId(),
        doc.getSpanId(),
        doc.getParentSpanId(),
        doc.getTimestamp(),
        doc.getLevel(),
        doc.getService(),
        doc.getEnvironment(),
        doc.getHost(),
        doc.getDurationMs()
    );
  }

  private List<Object> extractNextCursor(SearchHits<?> hits) {
    List<? extends SearchHit<?>> searchHits = hits.getSearchHits();
    if (searchHits.isEmpty()) {
      return null;
    }
    List<Object> sortValues = searchHits.get(searchHits.size() - 1).getSortValues();
    return sortValues.isEmpty() ? null : sortValues;
  }

  /**
   * avg 집계 결과에서 double 값을 추출한다.
   *
   * <p>결과 접근 경로: ElasticsearchAggregations → ElasticsearchAggregation
   * → Aggregation(name+Aggregate 래퍼) → Aggregate → AvgAggregate → value()
   */
  private Double extractAvgValue(SearchHits<?> hits, String aggName) {
    if (hits.getAggregations() == null) {
      return null;
    }
    try {
      ElasticsearchAggregations aggs = (ElasticsearchAggregations) hits.getAggregations();
      ElasticsearchAggregation  agg  = aggs.get(aggName);
      if (agg == null) return null;
      double value = agg.aggregation().getAggregate().avg().value();
      return (Double.isNaN(value) || Double.isInfinite(value)) ? null : value;
    } catch (Exception e) {
      log.warn("avg 집계 결과 추출 실패 [aggName={}]: {}", aggName, e.getMessage());
      return null;
    }
  }
}
