package com.logSystem.common.elasticsearch.service;

import com.logSystem.common.elasticsearch.document.ApiLogDocument;
import com.logSystem.common.elasticsearch.document.BaseDocument;
import com.logSystem.common.elasticsearch.document.DbLogDocument;
import com.logSystem.common.elasticsearch.document.ErrorLogDocument;
import com.logSystem.common.elasticsearch.document.ExternalApiLogDocument;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch Bulk 인덱싱 서비스.
 *
 * <p>역할:
 * <ul>
 *   <li>애플리케이션 시작 시 4개 인덱스 존재 여부 확인 후 자동 생성
 *   <li>Kafka 배치 레코드를 logType별로 분류하여 인덱스별 Bulk 인덱싱 수행
 *   <li>document ID = {@code {topic}-{partition}-{offset}} 로 재처리 시 멱등성 보장
 * </ul>
 *
 * <p>인덱스 초기화 전략: {@link ApplicationReadyEvent} 시점에 실행하여
 * ES 연결이 준비된 이후 인덱스를 생성한다.
 *
 * @author Yuri-JUNG
 */
@Service
public class ElasticsearchIndexService {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexService.class);

  private final ElasticsearchOperations esOperations;
  private final ObjectMapper            objectMapper;

  public ElasticsearchIndexService(ElasticsearchOperations esOperations,
                                   ObjectMapper objectMapper) {
    this.esOperations = esOperations;
    this.objectMapper = objectMapper;
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 인덱스 초기화
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * 애플리케이션 시작 후 4개 로그 인덱스를 생성한다.
   *
   * <p>이미 존재하는 인덱스는 건너뛰므로 재시작 시 안전하게 호출된다.
   * ES가 미연결 상태(테스트 환경 포함)이면 경고 로그만 남기고 계속 진행한다.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void initializeIndices() {
    try {
      createIfAbsent(ApiLogDocument.class);
      createIfAbsent(DbLogDocument.class);
      createIfAbsent(ExternalApiLogDocument.class);
      createIfAbsent(ErrorLogDocument.class);
    } catch (Exception e) {
      log.warn("ES 인덱스 초기화 실패 — ES 미연결 상태일 수 있습니다: {}", e.getMessage());
    }
  }

  private void createIfAbsent(Class<?> clazz) {
    IndexOperations indexOps = esOperations.indexOps(clazz);
    if (!indexOps.exists()) {
      indexOps.createWithMapping();
      log.info("ES 인덱스 생성 완료: {}", clazz.getSimpleName());
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Bulk 인덱싱
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Kafka 배치 레코드를 logType별로 분류하고 인덱스별 Bulk 인덱싱을 실행한다.
   *
   * <p>파싱에 실패한 레코드는 건너뛰고 경고 로그만 남긴다.
   * ES Bulk 실패 시 예외를 던져 {@code DefaultErrorHandler}의 재시도 흐름에 위임한다.
   *
   * @param records Kafka ConsumerRecord 배치
   * @throws RuntimeException ES Bulk 실패 시 — Kafka error handler가 재시도/DLQ 처리
   */
  public void bulkIndex(List<ConsumerRecord<String, String>> records) {
    List<ApiLogDocument>         apiDocs      = new ArrayList<>();
    List<DbLogDocument>          dbDocs       = new ArrayList<>();
    List<ExternalApiLogDocument> externalDocs = new ArrayList<>();
    List<ErrorLogDocument>       errorDocs    = new ArrayList<>();

    for (ConsumerRecord<String, String> record : records) {
      try {
        JsonNode root    = objectMapper.readTree(record.value());
        String   logType = text(root, "logType");
        String   docId   = toDocId(record);

        switch (logType) {
          case "API"          -> apiDocs.add(toApiDoc(root, docId));
          case "DB"           -> dbDocs.add(toDbDoc(root, docId));
          case "EXTERNAL_API" -> externalDocs.add(toExternalDoc(root, docId));
          case "ERROR"        -> errorDocs.add(toErrorDoc(root, docId));
          default             -> log.warn("알 수 없는 logType 건너뜀 [logType={}, offset={}]",
                                          logType, record.offset());
        }
      } catch (Exception e) {
        log.warn("레코드 파싱 실패 건너뜀 [topic={}, offset={}] cause={}",
                 record.topic(), record.offset(), e.getMessage());
      }
    }

    executeBulk(ApiLogDocument.class,         apiDocs);
    executeBulk(DbLogDocument.class,          dbDocs);
    executeBulk(ExternalApiLogDocument.class, externalDocs);
    executeBulk(ErrorLogDocument.class,       errorDocs);
  }

  /**
   * 단일 인덱스에 대한 Bulk 인덱싱을 실행한다.
   *
   * <p>document ID가 동일한 문서는 upsert로 처리되므로 재시도 시에도 중복이 발생하지 않는다.
   */
  private <T extends BaseDocument> void executeBulk(Class<T> clazz, List<T> docs) {
    if (docs.isEmpty()) {
      return;
    }

    List<IndexQuery> queries = docs.stream()
        .map(doc -> new IndexQueryBuilder()
            .withId(doc.getId())
            .withObject(doc)
            .build())
        .toList();

    esOperations.bulkIndex(queries, clazz);

    log.debug("ES Bulk 인덱싱 완료 [index={}, count={}]", clazz.getSimpleName(), docs.size());
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Document 변환
  // ──────────────────────────────────────────────────────────────────────────

  private ApiLogDocument toApiDoc(JsonNode root, String docId) {
    JsonNode http    = root.path("payload").path("http");
    JsonNode request = root.path("payload").path("request");

    return ApiLogDocument.builder()
        .id(docId)
        .traceId(text(root, "traceId"))
        .spanId(text(root, "spanId"))
        .parentSpanId(textOrNull(root, "parentSpanId"))
        .timestamp(parseInstant(root))
        .level(text(root, "level"))
        .service(text(root, "service"))
        .environment(textOrNull(root, "environment"))
        .host(textOrNull(root, "host"))
        .logType(text(root, "logType"))
        .durationMs(longOrNull(root, "durationMs"))
        .method(text(http, "method"))
        .path(text(http, "path"))
        .pathRaw(textOrNull(http, "pathRaw"))
        .query(textOrNull(http, "query"))
        .statusCode(intOrNull(http, "statusCode"))
        .clientIp(textOrNull(request, "clientIp"))
        .userAgent(textOrNull(request, "userAgent"))
        .build();
  }

  private DbLogDocument toDbDoc(JsonNode root, String docId) {
    JsonNode db = root.path("payload").path("db");

    return DbLogDocument.builder()
        .id(docId)
        .traceId(text(root, "traceId"))
        .spanId(text(root, "spanId"))
        .parentSpanId(textOrNull(root, "parentSpanId"))
        .timestamp(parseInstant(root))
        .level(text(root, "level"))
        .service(text(root, "service"))
        .environment(textOrNull(root, "environment"))
        .host(textOrNull(root, "host"))
        .logType(text(root, "logType"))
        .durationMs(longOrNull(root, "durationMs"))
        .operation(text(db, "operation"))
        .mapper(textOrNull(db, "mapper"))
        .table(textOrNull(db, "table"))
        .query(textOrNull(db, "query"))
        .rowCount(intOrNull(db, "rowCount"))
        .isSlow(boolOrNull(db, "isSlow"))
        .build();
  }

  private ExternalApiLogDocument toExternalDoc(JsonNode root, String docId) {
    JsonNode target = root.path("payload").path("target");

    return ExternalApiLogDocument.builder()
        .id(docId)
        .traceId(text(root, "traceId"))
        .spanId(text(root, "spanId"))
        .parentSpanId(textOrNull(root, "parentSpanId"))
        .timestamp(parseInstant(root))
        .level(text(root, "level"))
        .service(text(root, "service"))
        .environment(textOrNull(root, "environment"))
        .host(textOrNull(root, "host"))
        .logType(text(root, "logType"))
        .durationMs(longOrNull(root, "durationMs"))
        .targetService(textOrNull(target, "service"))
        .url(textOrNull(target, "url"))
        .method(textOrNull(target, "method"))
        .statusCode(intOrNull(target, "statusCode"))
        .isTimeout(boolOrNull(target, "isTimeout"))
        .retryCount(intOrNull(target, "retryCount"))
        .circuitBreakerState(textOrNull(target, "circuitBreakerState"))
        .build();
  }

  private ErrorLogDocument toErrorDoc(JsonNode root, String docId) {
    JsonNode error   = root.path("payload").path("error");
    JsonNode context = root.path("payload").path("context");

    return ErrorLogDocument.builder()
        .id(docId)
        .traceId(text(root, "traceId"))
        .spanId(text(root, "spanId"))
        .parentSpanId(textOrNull(root, "parentSpanId"))
        .timestamp(parseInstant(root))
        .level(text(root, "level"))
        .service(text(root, "service"))
        .environment(textOrNull(root, "environment"))
        .host(textOrNull(root, "host"))
        .logType(text(root, "logType"))
        .durationMs(longOrNull(root, "durationMs"))
        .errorCode(textOrNull(error, "code"))
        .exceptionClass(textOrNull(error, "type"))
        .errorMessage(textOrNull(error, "message"))
        .stackTrace(textOrNull(error, "stackTrace"))
        .httpMethod(textOrNull(context, "httpMethod"))
        .httpPath(textOrNull(context, "httpPath"))
        .httpStatus(intOrNull(context, "httpStatus"))
        .build();
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 파싱 헬퍼
  // ──────────────────────────────────────────────────────────────────────────

  /** Kafka 레코드 위치 기반 결정적 document ID 생성 */
  private String toDocId(ConsumerRecord<?, ?> record) {
    return record.topic() + "-" + record.partition() + "-" + record.offset();
  }

  private Instant parseInstant(JsonNode root) {
    String ts = root.path("timestamp").asText("");
    return ts.isBlank() ? Instant.now() : Instant.parse(ts);
  }

  private String text(JsonNode node, String field) {
    return node.path(field).asText("");
  }

  private String textOrNull(JsonNode node, String field) {
    JsonNode n = node.path(field);
    return (n.isMissingNode() || n.isNull()) ? null : n.asText();
  }

  private Long longOrNull(JsonNode node, String field) {
    JsonNode n = node.path(field);
    return (n.isMissingNode() || n.isNull()) ? null : n.asLong();
  }

  private Integer intOrNull(JsonNode node, String field) {
    JsonNode n = node.path(field);
    return (n.isMissingNode() || n.isNull()) ? null : n.asInt();
  }

  private Boolean boolOrNull(JsonNode node, String field) {
    JsonNode n = node.path(field);
    return (n.isMissingNode() || n.isNull()) ? null : n.asBoolean();
  }
}
