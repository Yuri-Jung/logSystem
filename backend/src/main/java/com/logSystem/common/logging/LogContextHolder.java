package com.logSystem.common.logging;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * MDC 기반 트레이스 컨텍스트 래퍼.
 *
 * <p>span 계층 흐름:
 * <pre>
 *   TraceFilter → initialize(traceId, rootSpanId)      ← 요청 최초 진입
 *   Controller  → startChildSpan() : parentSpanId 반환  ← DB/External 작업 시작
 *   Controller  → endChildSpan(parentSpanId)            ← 작업 완료 후 복원
 *   TraceFilter → clear()                               ← 응답 반환 직전
 * </pre>
 */
public final class LogContextHolder {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String SPAN_ID_KEY  = "spanId";

    private LogContextHolder() {}

    /** 요청 진입 시 traceId와 루트 spanId를 MDC에 등록 */
    public static void initialize(String traceId, String spanId) {
        MDC.put(TRACE_ID_KEY, traceId);
        MDC.put(SPAN_ID_KEY, spanId);
    }

    public static String getTraceId() { return MDC.get(TRACE_ID_KEY); }
    public static String getSpanId()  { return MDC.get(SPAN_ID_KEY); }

    /**
     * 자식 span을 시작한다.
     * 현재 spanId를 parentSpanId로 반환하고, MDC의 spanId를 새 UUID로 교체한다.
     *
     * @return 부모 spanId (작업 완료 후 {@link #endChildSpan}에 전달해야 함)
     */
    public static String startChildSpan() {
        String parentSpanId = MDC.get(SPAN_ID_KEY);
        MDC.put(SPAN_ID_KEY, UUID.randomUUID().toString());
        return parentSpanId;
    }

    /**
     * 자식 span을 종료하고 부모 spanId를 MDC에 복원한다.
     * try-finally 블록 안에서 반드시 호출해야 한다.
     */
    public static void endChildSpan(String parentSpanId) {
        if (parentSpanId != null) {
            MDC.put(SPAN_ID_KEY, parentSpanId);
        }
    }

    /** 스레드 풀 오염 방지 — 요청 처리 완료 후 반드시 호출 */
    public static void clear() {
        MDC.clear();
    }
}
