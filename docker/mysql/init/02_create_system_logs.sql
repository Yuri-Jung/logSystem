-- ============================================================
-- Kafka Consumer 저장 테이블
-- ============================================================
-- 설계 원칙:
-- - 토픽 4개(api / db / external / error)를 단일 테이블로 통합 수용
-- - payload 컬럼: 타입별 상세를 JSON으로 보존 (스키마 변경에 유연)
-- - logged_at : Producer 기록 시각 (비즈니스 기준 시간)
-- - consumed_at: Consumer 저장 시각 (처리 지연 측정용)
-- ============================================================

USE logsystem;

CREATE TABLE IF NOT EXISTS system_logs (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    log_type     VARCHAR(20)   NOT NULL COMMENT 'API | DB | EXTERNAL_API | ERROR',
    trace_id     VARCHAR(36)   NOT NULL COMMENT '요청 흐름 식별자',
    span_id      VARCHAR(36)   NOT NULL COMMENT '작업 단위 식별자',
    parent_span_id VARCHAR(36) COMMENT '부모 span. 최초 진입점은 NULL',
    service      VARCHAR(100)  NOT NULL COMMENT '로그 발생 서비스',
    level        VARCHAR(10)   NOT NULL COMMENT 'INFO | WARN | ERROR',
    duration_ms  BIGINT        COMMENT '처리 시간(ms). 타입에 따라 없을 수 있음',
    payload      JSON          COMMENT '타입별 상세 페이로드 (원본 JSON 보존)',
    logged_at    DATETIME(3)   NOT NULL COMMENT 'Producer 기록 시각',
    consumed_at  DATETIME(3)   NOT NULL COMMENT 'Consumer 저장 시각',

    PRIMARY KEY (id),

    INDEX idx_trace    (trace_id),
    INDEX idx_type_time (log_type, logged_at),
    INDEX idx_level    (level),
    INDEX idx_service  (service)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Kafka Consumer 소비 로그 통합 테이블';
