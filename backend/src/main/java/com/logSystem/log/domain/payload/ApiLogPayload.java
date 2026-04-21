package com.logSystem.log.domain.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.logSystem.log.domain.LogType;

import java.util.List;
import java.util.Map;

/**
 * 인입 HTTP 요청/응답 로그 페이로드.
 * TraceId를 최초 생성하는 진입점(parentSpanId = null).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiLogPayload(

        /** Jackson 타입 discriminator — 항상 {@link LogType#API} */
        LogType logType,

        /** HTTP 트랜잭션 핵심 정보 */
        Http http,

        /** 수신 요청 상세 */
        RequestDetail request,

        /** 송신 응답 상세 */
        ResponseDetail response,

        /** 인증/인가 컨텍스트 */
        AuthContext auth

) implements LogPayload {

    /** logType 불변성 보장: 역직렬화 시 전달된 값을 무시하고 API로 고정 */
    public ApiLogPayload {
        logType = LogType.API;
    }

    // ──────────────────────────────────────────────────────
    // 중첩 record
    // ──────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Http(
            /** GET | POST | PUT | PATCH | DELETE */
            String method,

            /** 경로 파라미터 템플릿화된 URI (e.g. /api/logs/{id}) */
            String path,

            /** 실제 요청 URI (e.g. /api/logs/42) */
            String pathRaw,

            /** 쿼리스트링 원문 */
            String query,

            int statusCode,

            /** 요청 수신 → 응답 완료 처리 시간 (ms) */
            int durationMs
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequestDetail(
            /** Authorization 등 민감 헤더는 마스킹 후 저장 */
            Map<String, String> headers,

            /** 요청 바디. password 등 민감 필드 마스킹 필수 */
            Object body,

            Integer bodySize,

            /** X-Forwarded-For 우선 적용 */
            String clientIp,

            String userAgent,
            String referer
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseDetail(
            Map<String, String> headers,

            /** 에러 응답일 때 주로 기록 */
            Object body,

            Integer bodySize
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthContext(
            /** 미인증 요청은 null */
            String userId,
            List<String> roles
    ) {}
}
