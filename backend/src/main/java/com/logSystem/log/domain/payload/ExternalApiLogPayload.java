package com.logSystem.log.domain.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.logSystem.log.domain.LogType;

import java.util.Map;

/**
 * 아웃바운드 외부 API 호출 로그 페이로드.
 * RestTemplate / WebClient / HttpClient 인터셉터로 수집.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalApiLogPayload(

        LogType logType,

        /** 호출 대상 서비스 정보 */
        TargetInfo target,

        /** 아웃바운드 요청 상세 */
        OutboundRequest request,

        /** 인바운드 응답 상세 */
        InboundResponse response

) implements LogPayload {

    public ExternalApiLogPayload {
        logType = LogType.EXTERNAL_API;
    }

    // ──────────────────────────────────────────────────────
    // 중첩 record
    // ──────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TargetInfo(
            /** 호출 대상 서비스 이름 (e.g. slack-api, payment-service) */
            String service,

            /** 호출한 전체 URL (쿼리스트링 포함) */
            String url,

            String method,

            /** 응답 HTTP 상태 코드. 타임아웃/네트워크 오류 시 null */
            Integer statusCode,

            /** 요청 발송 → 응답 수신 완료까지의 시간 (ms) */
            int durationMs,

            boolean isTimeout,

            /** 재시도 횟수 (0 = 최초 시도에서 완료) */
            int retryCount,

            /** Resilience4j 등 서킷브레이커 상태: CLOSED | OPEN | HALF_OPEN */
            String circuitBreakerState
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutboundRequest(
            /** API Key 등 민감 헤더는 마스킹 필수 */
            Map<String, String> headers,

            Object body,

            Integer bodySize
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InboundResponse(
            Map<String, String> headers,

            /** 실패/에러 응답 시 주로 기록 */
            Object body,

            Integer bodySize
    ) {}
}
