package com.logSystem.log.domain.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.logSystem.log.domain.LogType;

import java.time.Instant;
import java.util.Map;

/**
 * 예외/에러 발생 로그 페이로드.
 * 다른 로그 타입에서 에러가 발생하면 해당 span과 동일한 traceId로 ErrorLog를 별도 생성하여 연결한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErrorLogPayload(

        LogType logType,

        /** 예외/에러 상세 정보 */
        ErrorDetail error,

        /** 에러가 발생한 작업의 컨텍스트 */
        ErrorContext context,

        /** 알림 발송 추적 */
        AlertInfo alert

) implements LogPayload {

    public ErrorLogPayload {
        logType = LogType.ERROR;
    }

    // ──────────────────────────────────────────────────────
    // 중첩 record
    // ──────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorDetail(
            /** 도메인 정의 에러 코드 (e.g. ERR_DB_TIMEOUT, ERR_VALIDATION_FAILED) */
            String code,

            /** 예외 클래스 전체 경로 (e.g. java.sql.SQLTimeoutException) */
            String type,

            String message,

            /** 스택 트레이스. prod 환경에서는 민감 정보 제거 후 기록 */
            String stackTrace,

            /** 원인 예외 (중첩 예외 체인의 첫 번째 cause) */
            CauseDetail cause
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CauseDetail(
            String type,
            String message,
            String stackTrace
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorContext(
            /** 에러를 유발한 원본 로그 타입: API | DB | EXTERNAL_API */
            LogType originLogType,

            /** 에러가 발생한 원본 span의 spanId */
            String originSpanId,

            String httpMethod,
            String httpPath,

            /** 클라이언트에 반환된 HTTP 상태 코드 */
            Integer httpStatus,

            String userId,

            /** 에러 재현에 필요한 추가 컨텍스트 (자유 형식) */
            Map<String, Object> additionalInfo
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlertInfo(
            boolean sent,

            /** SLACK | EMAIL | PAGERDUTY | NONE */
            String channel,

            Instant sentAt
    ) {}
}
