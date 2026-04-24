package com.logSystem.common.kafka.consumer;

import com.logSystem.common.kafka.LogTopic;
import com.logSystem.log.domain.SystemLog;
import com.logSystem.log.repository.SystemLogMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * Kafka 로그 토픽 Consumer.
 *
 * <p>4개 토픽({@code log-api}, {@code log-db}, {@code log-external}, {@code log-error})을
 * 단일 리스너로 처리한다. 동일한 저장 로직을 공유하므로 토픽별 분리는 불필요하다.
 *
 * <p>역직렬화 전략:
 * <ul>
 *   <li>최상위 스칼라 필드는 {@code JsonNode}로 안전하게 추출
 *   <li>{@code payload}는 타입별 다형성 구조에 의존하지 않고 원본 JSON 문자열로 보존
 *   <li>이를 통해 Consumer가 Producer 도메인 모델 변경에 독립적으로 동작
 * </ul>
 *
 * <p>오류 처리 전략:
 * <ul>
 *   <li>역직렬화 실패: 재시도해도 동일하게 실패하므로 즉시 커밋 후 건너뜀
 *   <li>DB 저장 실패: {@link RuntimeException}을 던져 {@code DefaultErrorHandler}의
 *       지수 백오프 재시도에 위임
 * </ul>
 *
 * @author Yuri-JUNG
 */
@Component
public class LogKafkaConsumer {

  private static final Logger log = LoggerFactory.getLogger(LogKafkaConsumer.class);

  private final ObjectMapper     objectMapper;
  private final SystemLogMapper  systemLogMapper;

  public LogKafkaConsumer(ObjectMapper objectMapper, SystemLogMapper systemLogMapper) {
    this.objectMapper    = objectMapper;
    this.systemLogMapper = systemLogMapper;
  }

  /**
   * 모든 로그 토픽의 메시지를 소비하여 {@code system_logs} 테이블에 저장한다.
   *
   * <p>정상 처리 흐름:
   * <ol>
   *   <li>원본 JSON 문자열을 {@code JsonNode}로 파싱
   *   <li>스칼라 필드 추출 및 {@code SystemLog} 도메인 객체 생성
   *   <li>MySQL 저장 성공 후 오프셋 커밋({@code ack.acknowledge()})
   * </ol>
   *
   * @param record Kafka ConsumerRecord — topic, partition, offset 메타데이터 포함
   * @param ack    수동 오프셋 커밋 핸들러
   */
  @KafkaListener(
      topics = {
          LogTopic.API_TOPIC,
          LogTopic.DB_TOPIC,
          LogTopic.EXTERNAL_API_TOPIC,
          LogTopic.ERROR_TOPIC
      },
      groupId         = "log-consumer-group",
      containerFactory = "kafkaListenerContainerFactory"
  )
  public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
    String raw = record.value(); // Kafka에서 꺼낸 메시지 json 문자열
    // 1. JSON → JsonNode (파싱 실패 시 건너뜀)
    JsonNode root;
    try {
      root = objectMapper.readTree(raw);
    } catch (JacksonException e) {
      // JSON 자체가 깨진 경우 — 재시도해도 동일하게 실패하므로 건너뜀
      log.error(
          "역직렬화 실패: JSON 파싱 불가 [topic={}, partition={}, offset={}] raw={}",
          record.topic(), record.partition(), record.offset(), raw
      );
      ack.acknowledge(); // 파싱 불가 메시지를 건너뛰어 Consumer 블로킹 방지
      return;
    }
    // 2. SystemLog 도메인 객체 생성 → MySQL 저장
    try {
      SystemLog systemLog = toSystemLog(root);  // ← MySQL INSERT 실행
      systemLogMapper.insert(systemLog);        // 저장 성공 후 오프셋 커밋
      ack.acknowledge();

      log.debug(
          "로그 저장 완료 [topic={}, traceId={}, logType={}]",
          record.topic(),
          text(root, "traceId"),
          text(root, "logType")
      );

    } catch (Exception e) {
      // DB 저장 실패 — DefaultErrorHandler의 지수 백오프 재시도에 위임
      // ack를 호출하지 않으므로 오프셋이 커밋되지 않아 재처리 보장
      // ack 미호출 → 오프셋 미커밋 → 재처리 보장
      log.error(
          "로그 저장 실패: 재시도 예정 [topic={}, partition={}, offset={}, traceId={}] cause={}",
          record.topic(), record.partition(), record.offset(),
          text(root, "traceId"), e.getMessage()
      );
      throw new RuntimeException("system_logs 저장 실패 — 재시도 가능", e);
    }
  }

  /**
   * {@code JsonNode}에서 필드를 추출하여 {@link SystemLog} 도메인 객체를 생성한다.
   *
   * <p>{@code payload} 노드는 타입 변환 없이 JSON 문자열 그대로 저장한다.
   * Jackson 3.x({@code tools.jackson})와 페이로드 어노테이션의 호환 여부에 무관하게 안전하다.
   *
   * @param root 파싱된 Kafka 메시지의 최상위 JSON 노드
   * @return 저장 준비된 {@code SystemLog}
   */
  private SystemLog toSystemLog(JsonNode root) {
    Instant timestamp = root.path("timestamp").isNull()
        ? null
        : Instant.parse(root.path("timestamp").asText());

    // payload 노드가 없으면 빈 JSON 객체로 대체(노드 통째로 json 문자열로 보존)
    // → Consumer가 Producer의 payload 구조 변경에 독립적
    JsonNode payloadNode = root.path("payload");
    String payloadJson = payloadNode.isMissingNode() ? "{}" : payloadNode.toString();

    return SystemLog.of(
        text(root, "logType"),  // "DB" or "API"
        text(root, "traceId"),
        text(root, "spanId"),
        textOrNull(root, "parentSpanId"), // null 가능
        text(root, "service"),
        text(root, "level"),
        longOrNull(root, "durationMs"),
        timestamp,
        payloadJson
    );
  }
  // consumedAt = LocalDateTime.now() 자동 설정됨 (SystemLog.of 내부)
  
  /** 필드 값을 문자열로 반환. 필드가 없으면 빈 문자열 반환 */
  private String text(JsonNode node, String field) {
    return node.path(field).asText("");
  }

  /** 필드 값을 문자열로 반환. 필드가 없거나 null이면 null 반환 */
  private String textOrNull(JsonNode node, String field) {
    JsonNode target = node.path(field);
    return (target.isMissingNode() || target.isNull()) ? null : target.asText();
  }

  /** 필드 값을 Long으로 반환. 필드가 없거나 null이면 null 반환 */
  private Long longOrNull(JsonNode node, String field) {
    JsonNode target = node.path(field);
    return (target.isMissingNode() || target.isNull()) ? null : target.asLong();
  }
}
