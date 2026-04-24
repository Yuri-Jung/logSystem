package com.logSystem.common.kafka;

import com.logSystem.log.domain.LogEntry;
import com.logSystem.log.domain.LogLevel;
import com.logSystem.log.domain.payload.ApiLogPayload;
import com.logSystem.log.domain.payload.DbLogPayload;
import com.logSystem.log.domain.payload.ErrorLogPayload;
import com.logSystem.log.domain.payload.ExternalApiLogPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnBean(name = "kafkaTemplate")
public class LogKafkaProducer {

    private static final Logger log        = LoggerFactory.getLogger(LogKafkaProducer.class);
    private static final double SAMPLE_RATE = 0.10;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public LogKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper  = objectMapper;
    }

    /**
     * Kafka로 로그 전송
     * 샘플링 → 직렬화 → 전송
     * @param entry
     */
    public void send(LogEntry entry) {
        // 1. 샘플링 판단
        if (!shouldSample(entry)) {
            return;
        }
        // 2. 토픽 결정 (payload의 logType 기준)
        // /api/test/success 에서 writeDbLog() → "log-db" 토픽
        // TraceFilter에서 writeApiLog()       → "log-api" 토픽
        String topic = LogTopic.from(entry.payload().logType()).topicName;

        // 3. LogMessage record 생성 (Kafka 전송 전용 구조체) → 직렬화 대상
        LogMessage message = new LogMessage(
                entry.payload().logType().name(),
                entry.traceId(),
                entry.spanId(),
                entry.parentSpanId(),
                entry.service(),
                entry.level().name(),
                extractDurationMs(entry),
                entry.timestamp(),
                entry.payload()
        );

        String json;
        try {
            // 4. JSON 직렬화
            json = objectMapper.writeValueAsString(message);
        } catch (JacksonException e) {
            log.error("Kafka message serialization failed [traceId={}] cause={}", entry.traceId(), e.getMessage());
            return;
        }
        // 5. Kafka 비동기 전송 (key = traceId → 같은 파티션 보장)
        kafkaTemplate.send(topic, message.traceId(), json)
                .whenComplete((SendResult<String, String> result, Throwable ex) -> {
                    if (ex != null) {
                        log.error("Kafka send failed [traceId={}] topic={} cause={}",
                                message.traceId(), topic, ex.getMessage());
                    }
                });
    }

    /**
     * 샘플링 여부 판단
     * ERROR 레벨 또는 처리 시간이 1초 이상인 경우 무조건 샘플링
     * 그 외 10% 확률로 샘플링
     * @param entry
     * @return
     */
    private boolean shouldSample(LogEntry entry) {
        if (entry.level() == LogLevel.ERROR) {
            return true;
        }
        Long durationMs = extractDurationMs(entry);
        if (durationMs != null && durationMs >= 1_000) {
            return true;
        }
        return Math.abs(entry.traceId().hashCode()) % 100 < SAMPLE_RATE;
    }

    /**
     * 처리 시간 추출
     * @param entry
     * @return
     */
    private Long extractDurationMs(LogEntry entry) {
        if (entry.payload() instanceof ApiLogPayload p && p.http() != null) {
            return (long) p.http().durationMs();
        }
        if (entry.payload() instanceof DbLogPayload p && p.db() != null) {
            return (long) p.db().durationMs();
        }
        if (entry.payload() instanceof ExternalApiLogPayload p && p.target() != null) {
            return (long) p.target().durationMs();
        }
        return null;
    }
}
