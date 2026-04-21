package com.logSystem.common.exception;

import com.logSystem.common.logging.LogContextHolder;
import com.logSystem.common.logging.LogWriter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final LogWriter logWriter;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception [traceId={}] {}: {}",
                LogContextHolder.getTraceId(), e.getClass().getSimpleName(), e.getMessage());

        logWriter.writeErrorLog(e, request, HttpStatus.INTERNAL_SERVER_ERROR.value(), null);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error",   e.getMessage(),
                        "traceId", String.valueOf(LogContextHolder.getTraceId())
                ));
    }
}
