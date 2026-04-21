package com.logSystem.log.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.logSystem.log.domain.payload.LogPayload;

import java.time.Instant;

/**
 * 모든 로그 타입의 최상위 봉투(envelope).
 * - 공통 트레이싱 필드를 보유하고, 타입별 상세는 {@link LogPayload} 다형성 구현체에 위임.
 * - {@code traceId}는 요청 진입 시 최초 생성되어 모든 하위 span에 전파된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record LogEntry(

        /** 하나의 요청 흐름 전체를 묶는 UUID */
        String traceId,

        /** 이 로그가 속한 작업 단위 UUID */
        String spanId,

        /** 부모 span UUID. API 최초 진입점은 null */
        String parentSpanId,

        /** 로그 발생 시각 (UTC) */
        Instant timestamp,

        LogLevel level,
        String service,
        LogEnvironment environment,
        String host,
        String appVersion,
        String threadName,

        /**
         * 로그 타입별 상세 페이로드.
         * {@code @JsonTypeInfo}가 {@code logType} 필드를 기준으로 구현체를 결정한다.
         */
        LogPayload payload

) {
    /** 편의 메서드: 페이로드의 타입 반환 */
    public LogType logType() {
        return payload != null ? payload.logType() : null;
    }
}
