-- ============================================================
-- LogSystem Database Initialization Script
-- ============================================================
-- 목적:
-- 1. 분산 환경에서 발생하는 로그를 구조적으로 저장
-- 2. trace_id 기반으로 요청 흐름 추적 가능
-- 3. MySQL은 "저장/보관/집계", 조회는 Elasticsearch로 분리
--
-- 설계 철학:
-- - Write 성능 최적화 (Append Only)
-- - Trace 기반 조회 최적화
-- - 향후 Snowflake ID 확장 고려 (external_id)
-- ============================================================

-- ------------------------------------------------------------
-- 1. DATABASE 생성
-- ------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS logsystem
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE logsystem;

-- ============================================================
-- 2. API LOGS
-- ============================================================
-- 설명:
-- - 사용자 요청(Request/Response)을 기록
-- - 전체 trace의 시작점 역할
-- - HTTP 관련 정보 + 사용자 정보 포함

CREATE TABLE IF NOT EXISTS api_logs (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK (현재), 향후 Snowflake로 전환 가능',
    external_id BIGINT NULL COMMENT '분산 환경 대비 Snowflake ID',

    trace_id VARCHAR(36) NOT NULL COMMENT '요청 전체 흐름 식별자',
    span_id VARCHAR(36) NOT NULL COMMENT '현재 작업 단위 ID',
    parent_span_id VARCHAR(36) COMMENT '부모 span (최초 요청은 NULL)',

    http_method VARCHAR(10) NOT NULL COMMENT 'GET, POST 등',
    http_path VARCHAR(500) NOT NULL COMMENT 'API 경로 (/api/users)',
    http_path_raw VARCHAR(2000) COMMENT '실제 요청 URI',
    http_query VARCHAR(2000) COMMENT '쿼리 파라미터',

    status_code SMALLINT NOT NULL COMMENT 'HTTP 응답 코드',
    duration_ms INT NOT NULL COMMENT 'API 처리 시간',

    client_ip VARCHAR(45) NOT NULL COMMENT '클라이언트 IP',
    user_agent VARCHAR(500) COMMENT '브라우저/앱 정보',

    user_id VARCHAR(100) COMMENT '로그인 사용자 식별',

    service VARCHAR(100) NOT NULL COMMENT '서비스 이름 (user-service 등)',
    environment VARCHAR(20) NOT NULL COMMENT 'dev / prod',
    host VARCHAR(200) COMMENT '서버 호스트명',
    app_version VARCHAR(50) COMMENT '배포 버전',

    level VARCHAR(10) NOT NULL COMMENT 'INFO / WARN / ERROR',

    payload JSON COMMENT '추가 확장 데이터 (유연성 확보)',

    logged_at DATETIME(3) NOT NULL COMMENT '로그 발생 시각',

    PRIMARY KEY (id),

    -- 인덱스 전략
    INDEX idx_trace (trace_id),          -- trace 조회 핵심
    INDEX idx_span (span_id),            -- span 추적
    INDEX idx_time (logged_at),          -- 시간 기반 조회
    INDEX idx_status (status_code),      -- 에러 분석
    INDEX idx_service (service)          -- 서비스별 분석
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ============================================================
-- 3. DB LOGS
-- ============================================================
-- 설명:
-- - DB 쿼리 수행 정보 기록
-- - 병목 분석 핵심 데이터
-- - 어떤 쿼리가 느린지 추적 가능

CREATE TABLE IF NOT EXISTS db_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    external_id BIGINT NULL,

    trace_id VARCHAR(36) NOT NULL,
    span_id VARCHAR(36) NOT NULL,
    parent_span_id VARCHAR(36) NOT NULL COMMENT 'API span과 연결',

    datasource VARCHAR(100) NOT NULL COMMENT 'DB 이름',
    operation VARCHAR(10) NOT NULL COMMENT 'SELECT, INSERT 등',
    mapper_id VARCHAR(500) COMMENT 'MyBatis Mapper',

    target_table VARCHAR(200) COMMENT '대상 테이블',
    query TEXT COMMENT '실행된 SQL',

    duration_ms INT NOT NULL COMMENT '쿼리 실행 시간',
    row_count INT COMMENT '조회 결과 row 수',
    affected_rows INT COMMENT '변경 row 수',

    is_slow TINYINT(1) DEFAULT 0 COMMENT '슬로우 쿼리 여부',

    service VARCHAR(100) NOT NULL,
    environment VARCHAR(20) NOT NULL,

    level VARCHAR(10) NOT NULL,

    payload JSON,

    logged_at DATETIME(3) NOT NULL,

    PRIMARY KEY (id),

    INDEX idx_trace (trace_id),
    INDEX idx_span (span_id),
    INDEX idx_time (logged_at),
    INDEX idx_slow (is_slow)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ============================================================
-- 4. EXTERNAL API LOGS
-- ============================================================
-- 설명:
-- - 외부 시스템 호출 기록
-- - 결제 API, 인증 API 등 추적
-- - 네트워크 병목 분석 핵심

CREATE TABLE IF NOT EXISTS external_api_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    external_id BIGINT NULL,

    trace_id VARCHAR(36) NOT NULL,
    span_id VARCHAR(36) NOT NULL,
    parent_span_id VARCHAR(36) NOT NULL,

    target_service VARCHAR(100) NOT NULL COMMENT '외부 서비스 이름',
    target_url VARCHAR(2000) NOT NULL COMMENT '호출 URL',

    http_method VARCHAR(10) NOT NULL,
    status_code SMALLINT COMMENT '응답 코드 (타임아웃 시 NULL)',

    duration_ms INT NOT NULL,

    is_timeout TINYINT(1) DEFAULT 0,
    retry_count TINYINT DEFAULT 0,

    service VARCHAR(100) NOT NULL,
    environment VARCHAR(20) NOT NULL,

    level VARCHAR(10) NOT NULL,

    payload JSON,

    logged_at DATETIME(3) NOT NULL,

    PRIMARY KEY (id),

    INDEX idx_trace (trace_id),
    INDEX idx_span (span_id),
    INDEX idx_time (logged_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ============================================================
-- 5. ERROR LOGS
-- ============================================================
-- 설명:
-- - 예외 및 장애 로그
-- - 운영 대응용 핵심 테이블
-- - 알림 시스템과 연계 가능

CREATE TABLE IF NOT EXISTS error_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    external_id BIGINT NULL,

    trace_id VARCHAR(36) NOT NULL,
    span_id VARCHAR(36) NOT NULL,
    parent_span_id VARCHAR(36) NOT NULL,

    error_type VARCHAR(500) NOT NULL COMMENT 'Exception 클래스',
    error_message TEXT NOT NULL,
    stack_trace MEDIUMTEXT,

    origin_log_type VARCHAR(20) COMMENT 'API / DB / EXTERNAL',

    service VARCHAR(100) NOT NULL,
    environment VARCHAR(20) NOT NULL,

    level VARCHAR(10) NOT NULL,

    payload JSON,

    logged_at DATETIME(3) NOT NULL,

    PRIMARY KEY (id),

    INDEX idx_trace (trace_id),
    INDEX idx_span (span_id),
    INDEX idx_time (logged_at),
    INDEX idx_type (error_type(200))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;