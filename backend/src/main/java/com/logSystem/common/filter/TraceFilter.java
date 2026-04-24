package com.logSystem.common.filter;

import com.logSystem.common.logging.LogContextHolder;
import com.logSystem.common.logging.LogWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 모든 HTTP 요청의 최전방에서 실행되는 트레이싱 필터.
 *
 * <p>역할:
 * <ul>
 *   <li>traceId / rootSpanId 생성 → MDC 등록</li>
 *   <li>응답 헤더 {@code X-Trace-Id} 설정</li>
 *   <li>요청 처리 완료 후 API 로그 기록</li>
 *   <li>MDC 정리 (스레드 풀 오염 방지)</li>
 * </ul>
 *
 * <p>업스트림(API 게이트웨이 등)이 {@code X-Trace-Id} 헤더를 보내면
 * 새 UUID를 생성하지 않고 그 값을 그대로 사용하여 트레이스를 이어받는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TraceFilter extends OncePerRequestFilter {

    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    private final LogWriter logWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String traceId    = resolveTraceId(request);
        String rootSpanId = UUID.randomUUID().toString();

        LogContextHolder.initialize(traceId, rootSpanId);
        response.setHeader(HEADER_TRACE_ID, traceId);

        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response); // 요청 처리(API 컨트롤러 호출)
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            // rootSpanId를 직접 전달 — 자식 span이 복원되지 않은 예외 경로에서도 안전
            logWriter.writeApiLog(request, response, traceId, rootSpanId, durationMs);
            LogContextHolder.clear();
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String upstream = request.getHeader(HEADER_TRACE_ID);
        return StringUtils.hasText(upstream) ? upstream : UUID.randomUUID().toString();
    }
}
