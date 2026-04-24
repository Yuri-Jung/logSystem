package com.logSystem.common.kafka;

import com.logSystem.log.domain.payload.LogPayload;

import java.time.Instant;

/**
 * Kafka 전송 전용 메시지 봉투.
 *
 * <p>{@link com.logSystem.log.domain.LogEntry}의 모든 트레이싱 필드와 페이로드를 담아
 * JSON으로 직렬화된다. Consumer는 이 구조를 {@code JsonNode}로 역직렬화하여 사용한다.
 *
 * @param environment 실행 환경 (LOCAL | DEV | PROD)
 * @param host        로그를 발생시킨 호스트명
 */
public record LogMessage(
    String     logType,
    String     traceId,
    String     spanId,
    String     parentSpanId,
    String     service,
    String     level,
    Long       durationMs,
    Instant    timestamp,
    String     environment,
    String     host,
    LogPayload payload
) {}
