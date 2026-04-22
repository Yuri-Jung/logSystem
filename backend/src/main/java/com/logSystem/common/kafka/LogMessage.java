package com.logSystem.common.kafka;

import com.logSystem.log.domain.payload.LogPayload;

import java.time.Instant;

public record LogMessage(
        String     logType,
        String     traceId,
        String     spanId,
        String     parentSpanId,
        String     service,
        String     level,
        Long       durationMs,
        Instant    timestamp,
        LogPayload payload
) {}
