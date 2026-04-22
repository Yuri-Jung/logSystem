package com.logSystem.common.logging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.logSystem.common.kafka.LogKafkaProducer;
import com.logSystem.log.domain.*;
import com.logSystem.log.domain.payload.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class LogWriter {

    private static final Logger STRUCTURED = LoggerFactory.getLogger("STRUCTURED");

    private static final String SERVICE_NAME = "logsystem-backend";
    private static final String APP_VERSION  = "0.0.1-SNAPSHOT";

    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private LogKafkaProducer kafkaProducer;

    public LogWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ──────────────────────────────────────────────────────────────────────
    // API 로그 (TraceFilter에서 호출)
    // ──────────────────────────────────────────────────────────────────────

    public void writeApiLog(HttpServletRequest request,
                            HttpServletResponse response,
                            String traceId,
                            String rootSpanId,
                            long durationMs) {

        String pathTemplate = resolvePathTemplate(request);
        int    status       = response.getStatus();
        LogLevel level      = status >= 500 ? LogLevel.ERROR
                            : status >= 400 ? LogLevel.WARN
                            : LogLevel.INFO;

        ApiLogPayload payload = new ApiLogPayload(
                null,
                new ApiLogPayload.Http(
                        request.getMethod(),
                        pathTemplate,
                        request.getRequestURI(),
                        request.getQueryString(),
                        status,
                        (int) durationMs
                ),
                new ApiLogPayload.RequestDetail(
                        null,
                        null,
                        null,
                        resolveClientIp(request),
                        request.getHeader("User-Agent"),
                        request.getHeader("Referer")
                ),
                new ApiLogPayload.ResponseDetail(null, null, null),
                null
        );

        write(buildEntry(traceId, rootSpanId, null, level, payload));
    }

    // ──────────────────────────────────────────────────────────────────────
    // DB 로그 (컨트롤러/서비스에서 호출)
    // ──────────────────────────────────────────────────────────────────────

    public void writeDbLog(String parentSpanId,
                           String mapper,
                           String operation,
                           String table,
                           String query,
                           List<DbLogPayload.QueryParameter> parameters,
                           long durationMs,
                           Integer rowCount,
                           Integer affectedRows) {

        boolean isSlow = durationMs > 1_000;
        LogLevel level = isSlow ? LogLevel.WARN : LogLevel.INFO;

        DbLogPayload payload = new DbLogPayload(
                null,
                new DbLogPayload.DbContext(
                        "logsystem-primary",
                        operation,
                        mapper,
                        table,
                        query,
                        parameters,
                        (int) durationMs,
                        rowCount,
                        affectedRows,
                        isSlow,
                        "pool-conn-1",
                        null
                )
        );

        write(buildEntry(
                LogContextHolder.getTraceId(),
                LogContextHolder.getSpanId(),
                parentSpanId,
                level,
                payload
        ));
    }

    // ──────────────────────────────────────────────────────────────────────
    // 외부 API 로그
    // ──────────────────────────────────────────────────────────────────────

    public void writeExternalApiLog(String parentSpanId,
                                    String service,
                                    String url,
                                    String method,
                                    Integer statusCode,
                                    long durationMs,
                                    boolean isTimeout,
                                    int retryCount) {

        LogLevel level = (statusCode == null || statusCode >= 500 || isTimeout)
                       ? LogLevel.ERROR
                       : statusCode >= 400 ? LogLevel.WARN
                       : LogLevel.INFO;

        ExternalApiLogPayload payload = new ExternalApiLogPayload(
                null,
                new ExternalApiLogPayload.TargetInfo(
                        service, url, method, statusCode,
                        (int) durationMs, isTimeout, retryCount, "CLOSED"
                ),
                new ExternalApiLogPayload.OutboundRequest(null, null, null),
                new ExternalApiLogPayload.InboundResponse(null, null, null)
        );

        write(buildEntry(
                LogContextHolder.getTraceId(),
                LogContextHolder.getSpanId(),
                parentSpanId,
                level,
                payload
        ));
    }

    // ──────────────────────────────────────────────────────────────────────
    // 에러 로그 (GlobalExceptionHandler에서 호출)
    // ──────────────────────────────────────────────────────────────────────

    public void writeErrorLog(Throwable ex,
                              HttpServletRequest request,
                              int httpStatus,
                              String userId) {

        String traceId      = LogContextHolder.getTraceId();
        String parentSpanId = LogContextHolder.getSpanId(); // 에러 발생 시점의 span = 부모
        String errorSpanId  = UUID.randomUUID().toString();

        ErrorLogPayload payload = new ErrorLogPayload(
                null,
                new ErrorLogPayload.ErrorDetail(
                        "ERR_" + ex.getClass().getSimpleName().toUpperCase(),
                        ex.getClass().getName(),
                        ex.getMessage(),
                        toStackTrace(ex),
                        ex.getCause() != null ? new ErrorLogPayload.CauseDetail(
                                ex.getCause().getClass().getName(),
                                ex.getCause().getMessage(),
                                null
                        ) : null
                ),
                new ErrorLogPayload.ErrorContext(
                        LogType.API,
                        parentSpanId,
                        request.getMethod(),
                        request.getRequestURI(),
                        httpStatus,
                        userId,
                        null
                ),
                null
        );

        write(buildEntry(traceId, errorSpanId, parentSpanId, LogLevel.ERROR, payload));
    }

    // ──────────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────────────────────────────────

    private LogEntry buildEntry(String traceId,
                                String spanId,
                                String parentSpanId,
                                LogLevel level,
                                LogPayload payload) {
        return new LogEntry(
                traceId,
                spanId,
                parentSpanId,
                Instant.now(),
                level,
                SERVICE_NAME,
                LogEnvironment.LOCAL,
                resolveHostName(),
                APP_VERSION,
                Thread.currentThread().getName(),
                payload
        );
    }

    private void write(LogEntry entry) {
        try {
            String json = objectMapper.writeValueAsString(entry);
            switch (entry.level()) {
                case ERROR -> STRUCTURED.error(json);
                case WARN  -> STRUCTURED.warn(json);
                case DEBUG -> STRUCTURED.debug(json);
                case TRACE -> STRUCTURED.trace(json);
                default    -> STRUCTURED.info(json);
            }
        } catch (JacksonException e) {
            STRUCTURED.error("{\"error\":\"log serialization failed\",\"cause\":\"{}\"}", e.getMessage());
        }

        if (kafkaProducer != null) {
            kafkaProducer.send(entry);
        }
    }

    private String resolvePathTemplate(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : request.getRequestURI();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveHostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String toStackTrace(Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
