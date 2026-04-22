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

    public void send(LogEntry entry) {
        if (!shouldSample(entry)) {
            return;
        }

        String topic = LogTopic.from(entry.payload().logType()).topicName;

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
            json = objectMapper.writeValueAsString(message);
        } catch (JacksonException e) {
            log.error("Kafka message serialization failed [traceId={}] cause={}", entry.traceId(), e.getMessage());
            return;
        }

        kafkaTemplate.send(topic, message.traceId(), json)
                .whenComplete((SendResult<String, String> result, Throwable ex) -> {
                    if (ex != null) {
                        log.error("Kafka send failed [traceId={}] topic={} cause={}",
                                message.traceId(), topic, ex.getMessage());
                    }
                });
    }

    private boolean shouldSample(LogEntry entry) {
        if (entry.level() == LogLevel.ERROR) {
            return true;
        }
        Long durationMs = extractDurationMs(entry);
        if (durationMs != null && durationMs >= 1_000) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < SAMPLE_RATE;
    }

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
