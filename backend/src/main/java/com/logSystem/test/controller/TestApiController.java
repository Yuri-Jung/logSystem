package com.logSystem.test.controller;

import com.logSystem.common.logging.LogContextHolder;
import com.logSystem.common.logging.LogWriter;
import com.logSystem.log.domain.payload.DbLogPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 로그 파이프라인 동작 검증용 테스트 엔드포인트 모음.
 * 각 엔드포인트는 실제 운영에서 발생할 수 있는 시나리오를 흉내 낸다.
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestApiController {

    private final LogWriter logWriter;

    /**
     * 정상 응답 시나리오.
     * 즉각 응답하며 INFO 수준의 DB 로그를 발생시킨다.
     */
    @GetMapping("/success")
    public ResponseEntity<Map<String, Object>> success() {
        String parentSpanId = LogContextHolder.startChildSpan();
        long start = System.currentTimeMillis();
        try {
            // 실제 DB 호출을 흉내 낸 즉각 완료
            simulateInstantQuery();
            long duration = System.currentTimeMillis() - start;

            logWriter.writeDbLog(
                    parentSpanId,
                    "com.logSystem.log.repository.LogMapper.findAllLogs",
                    "SELECT",
                    "logs",
                    "SELECT id, level, message, source, created_at FROM logs ORDER BY created_at DESC LIMIT #{size}",
                    List.of(new DbLogPayload.QueryParameter(1, "size", 10, "INTEGER")),
                    duration,
                    10,
                    null
            );
        } finally {
            LogContextHolder.endChildSpan(parentSpanId);
        }

        log.debug("success endpoint processed [traceId={}]", LogContextHolder.getTraceId());

        return ResponseEntity.ok(Map.of(
                "status",  "OK",
                "message", "정상 응답입니다.",
                "traceId", LogContextHolder.getTraceId()
        ));
    }

    /**
     * 슬로우 쿼리 시나리오 (1.5초 지연).
     * isSlow=true, WARN 수준의 DB 로그를 발생시킨다.
     */
    @GetMapping("/slow-db")
    public ResponseEntity<Map<String, Object>> slowDb() throws InterruptedException {
        String parentSpanId = LogContextHolder.startChildSpan();
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(1_500); // 슬로우 쿼리 모사
            long duration = System.currentTimeMillis() - start;

            logWriter.writeDbLog(
                    parentSpanId,
                    "com.logSystem.log.repository.LogMapper.findLogsByCondition",
                    "SELECT",
                    "logs",
                    "SELECT * FROM logs WHERE created_at BETWEEN #{from} AND #{to} AND level = #{level} ORDER BY created_at DESC",
                    List.of(
                            new DbLogPayload.QueryParameter(1, "from",  "2026-01-01T00:00:00Z", "TIMESTAMP"),
                            new DbLogPayload.QueryParameter(2, "to",    "2026-04-21T23:59:59Z", "TIMESTAMP"),
                            new DbLogPayload.QueryParameter(3, "level", "ERROR",                "VARCHAR")
                    ),
                    duration,
                    0,
                    null
            );
        } finally {
            LogContextHolder.endChildSpan(parentSpanId);
        }

        return ResponseEntity.ok(Map.of(
                "status",     "OK",
                "message",    "슬로우 DB 쿼리 응답입니다. (1.5초 지연)",
                "durationMs", 1500,
                "traceId",    LogContextHolder.getTraceId()
        ));
    }

    /**
     * 외부 API 호출 시나리오 (2초 지연).
     * INFO 수준의 외부 API 로그를 발생시킨다.
     */
    @GetMapping("/external")
    public ResponseEntity<Map<String, Object>> external() throws InterruptedException {
        String parentSpanId = LogContextHolder.startChildSpan();
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(2_000); // 외부 API 응답 대기 모사
            long duration = System.currentTimeMillis() - start;

            logWriter.writeExternalApiLog(
                    parentSpanId,
                    "slack-webhook",
                    "https://hooks.slack.com/services/T00000/B00000/XXXXXXXX",
                    "POST",
                    200,
                    duration,
                    false,
                    0
            );
        } finally {
            LogContextHolder.endChildSpan(parentSpanId);
        }

        return ResponseEntity.ok(Map.of(
                "status",     "OK",
                "message",    "외부 API 호출 응답입니다. (2초 지연)",
                "durationMs", 2000,
                "traceId",    LogContextHolder.getTraceId()
        ));
    }

    /**
     * 에러 시나리오.
     * RuntimeException을 의도적으로 발생시킨다.
     * GlobalExceptionHandler가 에러 로그를 기록하고 500 응답을 반환한다.
     */
    @GetMapping("/error")
    public ResponseEntity<?> error() {
        log.warn("error endpoint called — intentional exception [traceId={}]", LogContextHolder.getTraceId());
        throw new RuntimeException("의도적으로 발생시킨 테스트 에러입니다. TraceId: " + LogContextHolder.getTraceId());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private void simulateInstantQuery() {
        // 실제 DB 쿼리를 대체하는 빠른 더미 연산
        long dummy = 0;
        for (int i = 0; i < 1_000; i++) dummy += i;
        log.trace("dummy={}", dummy);
    }
}
