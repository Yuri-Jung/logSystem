package com.logSystem.log.domain.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.logSystem.log.domain.LogType;

import java.util.List;

/**
 * DB 쿼리 실행 로그 페이로드.
 * MyBatis Interceptor 또는 AOP로 수집. parentSpanId = API span의 spanId.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DbLogPayload(

        LogType logType,

        /** 쿼리 실행 상세 */
        DbContext db

) implements LogPayload {

    public DbLogPayload {
        logType = LogType.DB;
    }

    // ──────────────────────────────────────────────────────
    // 중첩 record
    // ──────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DbContext(
            /** 데이터소스 식별자 (다중 DB 환경 대비, e.g. logsystem-primary) */
            String datasource,

            /** SELECT | INSERT | UPDATE | DELETE | CALL | DDL | BATCH */
            String operation,

            /** MyBatis Mapper 전체 경로 (e.g. com.logSystem.log.repository.LogMapper.insertLog) */
            String mapper,

            /** 주 대상 테이블명 */
            String table,

            /** 실행된 SQL. 바인딩 전 원본 (파라미터는 #{} 형태 유지) */
            String query,

            /** 바인딩 파라미터 목록. PII/민감 데이터 마스킹 필수 */
            List<QueryParameter> parameters,

            /** 쿼리 실행 시간 (ms). 슬로우 쿼리 기준값과 비교에 사용 */
            int durationMs,

            /** SELECT 결과 행 수 */
            Integer rowCount,

            /** INSERT/UPDATE/DELETE 영향 행 수 */
            Integer affectedRows,

            /** 슬로우 쿼리 여부 (기준: durationMs > 1000) */
            boolean isSlow,

            /** 커넥션 풀 내 커넥션 식별자 */
            String connectionId,

            /** 트랜잭션 범위 내 여러 쿼리를 묶기 위한 트랜잭션 ID */
            String transactionId
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QueryParameter(
            /** 바인딩 순서 (1-based) */
            Integer index,

            /** 파라미터 이름 (MyBatis named parameter) */
            String name,

            /** 실제 값. 민감 데이터는 "***MASKED***"로 치환 */
            Object value,

            /** JDBC 타입 (e.g. VARCHAR, BIGINT, TIMESTAMP) */
            String type
    ) {}
}
