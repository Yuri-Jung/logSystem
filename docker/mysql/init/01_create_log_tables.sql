-- ============================================================
-- LogSystem 로그 테이블 DDL
-- TraceId 기반 4개 로그 타입 + 공통 인덱스 전략
-- ============================================================

CREATE DATABASE IF NOT EXISTS logsystem
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE logsystem;

-- ──────────────────────────────────────────
-- 0. 기존 LogMapper가 참조하는 레거시 테이블
-- ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS log (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    level      VARCHAR(10)   NOT NULL,
    message    TEXT          NOT NULL,
    source     VARCHAR(200)  NOT NULL,
    created_at DATETIME      NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_log_level      (level),
    INDEX idx_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='레거시 단순 로그 테이블 (LogMapper 호환용)';

-- ──────────────────────────────────────────
-- 1. API 로그
-- ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS api_logs (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    trace_id        VARCHAR(36)     NOT NULL COMMENT 'UUID: 요청 흐름 전체 식별자',
    span_id         VARCHAR(36)     NOT NULL COMMENT 'UUID: 이 로그의 작업 단위 식별자',
    parent_span_id  VARCHAR(36)         NULL COMMENT 'UUID: 부모 span (최상위 진입점은 NULL)',

    -- HTTP
    http_method     VARCHAR(10)     NOT NULL,
    http_path       VARCHAR(500)    NOT NULL COMMENT '경로 파라미터 템플릿화된 URI',
    http_path_raw   VARCHAR(2000)       NULL COMMENT '실제 요청 URI',
    http_query      VARCHAR(2000)       NULL,
    status_code     SMALLINT        NOT NULL,
    duration_ms     INT             NOT NULL,

    -- 요청
    client_ip       VARCHAR(45)     NOT NULL,
    user_agent      VARCHAR(500)        NULL,

    -- 인증
    user_id         VARCHAR(100)        NULL,

    -- 공통
    level           VARCHAR(10)     NOT NULL,
    service         VARCHAR(100)    NOT NULL,
    environment     VARCHAR(20)     NOT NULL,
    host            VARCHAR(200)        NULL,
    app_version     VARCHAR(50)         NULL,
    logged_at       DATETIME(3)     NOT NULL COMMENT '로그 발생 시각 (밀리초 정밀도)',

    PRIMARY KEY (id),
    INDEX idx_api_trace_id   (trace_id),
    INDEX idx_api_span_id    (span_id),
    INDEX idx_api_logged_at  (logged_at),
    INDEX idx_api_status     (status_code),
    INDEX idx_api_path       (http_path(200))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 요청/응답 로그';


-- ──────────────────────────────────────────
-- 2. DB 로그
-- ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS db_logs (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    trace_id        VARCHAR(36)     NOT NULL,
    span_id         VARCHAR(36)     NOT NULL,
    parent_span_id  VARCHAR(36)     NOT NULL COMMENT '이 DB 호출을 유발한 API span_id',

    datasource      VARCHAR(100)    NOT NULL,
    operation       VARCHAR(10)     NOT NULL COMMENT 'SELECT|INSERT|UPDATE|DELETE|CALL|DDL|BATCH',
    mapper_id       VARCHAR(500)        NULL COMMENT 'MyBatis Mapper 전체 경로',
    target_table    VARCHAR(200)        NULL,
    query           TEXT            NOT NULL,
    duration_ms     INT             NOT NULL,
    row_count       INT                 NULL COMMENT 'SELECT 결과 행 수',
    affected_rows   INT                 NULL COMMENT 'INSERT/UPDATE/DELETE 영향 행 수',
    is_slow         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '슬로우 쿼리 여부 (1=느림)',
    transaction_id  VARCHAR(100)        NULL,

    level           VARCHAR(10)     NOT NULL,
    service         VARCHAR(100)    NOT NULL,
    environment     VARCHAR(20)     NOT NULL,
    host            VARCHAR(200)        NULL,
    app_version     VARCHAR(50)         NULL,
    logged_at       DATETIME(3)     NOT NULL,

    PRIMARY KEY (id),
    INDEX idx_db_trace_id   (trace_id),
    INDEX idx_db_span_id    (span_id),
    INDEX idx_db_logged_at  (logged_at),
    INDEX idx_db_is_slow    (is_slow),
    INDEX idx_db_operation  (operation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DB 쿼리 실행 로그';


-- ──────────────────────────────────────────
-- 3. 외부 API 로그
-- ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS external_api_logs (
    id                    BIGINT          NOT NULL AUTO_INCREMENT,
    trace_id              VARCHAR(36)     NOT NULL,
    span_id               VARCHAR(36)     NOT NULL,
    parent_span_id        VARCHAR(36)     NOT NULL,

    target_service        VARCHAR(100)    NOT NULL,
    target_url            VARCHAR(2000)   NOT NULL,
    http_method           VARCHAR(10)     NOT NULL,
    status_code           SMALLINT            NULL COMMENT '타임아웃 시 NULL',
    duration_ms           INT             NOT NULL,
    is_timeout            TINYINT(1)      NOT NULL DEFAULT 0,
    retry_count           TINYINT         NOT NULL DEFAULT 0,
    circuit_breaker_state VARCHAR(20)         NULL COMMENT 'CLOSED|OPEN|HALF_OPEN',

    level                 VARCHAR(10)     NOT NULL,
    service               VARCHAR(100)    NOT NULL,
    environment           VARCHAR(20)     NOT NULL,
    host                  VARCHAR(200)        NULL,
    app_version           VARCHAR(50)         NULL,
    logged_at             DATETIME(3)     NOT NULL,

    PRIMARY KEY (id),
    INDEX idx_ext_trace_id      (trace_id),
    INDEX idx_ext_span_id       (span_id),
    INDEX idx_ext_logged_at     (logged_at),
    INDEX idx_ext_target        (target_service),
    INDEX idx_ext_is_timeout    (is_timeout)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='외부 API 아웃바운드 호출 로그';


-- ──────────────────────────────────────────
-- 4. 에러 로그
-- ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS error_logs (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    trace_id          VARCHAR(36)     NOT NULL,
    span_id           VARCHAR(36)     NOT NULL,
    parent_span_id    VARCHAR(36)     NOT NULL COMMENT '에러가 발생한 원본 span_id',

    error_code        VARCHAR(100)        NULL,
    error_type        VARCHAR(500)    NOT NULL COMMENT '예외 클래스 전체 경로',
    error_message     TEXT            NOT NULL,
    stack_trace       MEDIUMTEXT          NULL,
    cause_type        VARCHAR(500)        NULL,
    cause_message     TEXT                NULL,

    -- 컨텍스트
    origin_log_type   VARCHAR(20)         NULL COMMENT 'API|DB|EXTERNAL_API',
    origin_span_id    VARCHAR(36)         NULL,
    http_method       VARCHAR(10)         NULL,
    http_path         VARCHAR(500)        NULL,
    http_status       SMALLINT            NULL,
    user_id           VARCHAR(100)        NULL,

    -- 알림
    alert_sent        TINYINT(1)      NOT NULL DEFAULT 0,
    alert_channel     VARCHAR(30)         NULL,
    alert_sent_at     DATETIME(3)         NULL,

    level             VARCHAR(10)     NOT NULL,
    service           VARCHAR(100)    NOT NULL,
    environment       VARCHAR(20)     NOT NULL,
    host              VARCHAR(200)        NULL,
    app_version       VARCHAR(50)         NULL,
    logged_at         DATETIME(3)     NOT NULL,

    PRIMARY KEY (id),
    INDEX idx_err_trace_id    (trace_id),
    INDEX idx_err_span_id     (span_id),
    INDEX idx_err_logged_at   (logged_at),
    INDEX idx_err_error_code  (error_code),
    INDEX idx_err_error_type  (error_type(200)),
    INDEX idx_err_level       (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='예외/에러 로그';
