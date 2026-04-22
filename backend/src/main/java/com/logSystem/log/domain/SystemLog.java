package com.logSystem.log.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Kafka Consumer가 소비한 로그를 {@code system_logs} 테이블에 저장하는 도메인 객체.
 *
 * <p>페이로드({@code payloadJson})는 타입별 다형성 구조 대신 원본 JSON 문자열로 보존한다.
 * 이를 통해 Consumer가 Producer의 도메인 모델 변경에 독립적으로 동작한다.
 *
 * @author Yuri-JUNG
 */
@Getter
@Builder
public class SystemLog {

  private Long          id;
  private String        logType;
  private String        traceId;
  private String        spanId;
  private String        parentSpanId;
  private String        service;
  private String        level;
  private Long          durationMs;
  /** 타입별 상세 페이로드 — 원본 JSON 그대로 보존 */
  private String        payloadJson;
  /** 로그 발생 시각 (Producer 기록 기준) */
  private LocalDateTime loggedAt;
  /** Consumer가 MySQL에 저장한 시각 */
  private LocalDateTime consumedAt;

  /**
   * Kafka 메시지에서 파싱된 값들로 {@code SystemLog}를 생성한다.
   *
   * @param logType     로그 타입 문자열 (API / DB / EXTERNAL_API / ERROR)
   * @param traceId     요청 흐름 전체 식별자
   * @param spanId      현재 작업 단위 식별자
   * @param parentSpanId 부모 span 식별자 (최초 진입점은 null)
   * @param service     로그를 발생시킨 서비스명
   * @param level       로그 레벨 (INFO / WARN / ERROR)
   * @param durationMs  처리 시간 (ms), 없으면 null
   * @param timestamp   로그 발생 시각 (Instant, UTC)
   * @param payloadJson 타입별 페이로드 원본 JSON 문자열
   * @return 저장 준비된 {@code SystemLog} 인스턴스
   */
  public static SystemLog of(
      String logType,
      String traceId,
      String spanId,
      String parentSpanId,
      String service,
      String level,
      Long durationMs,
      Instant timestamp,
      String payloadJson) {

    LocalDateTime loggedAt = timestamp != null
        ? LocalDateTime.ofInstant(timestamp, ZoneId.of("Asia/Seoul"))
        : LocalDateTime.now(ZoneId.of("Asia/Seoul"));

    return SystemLog.builder()
        .logType(logType)
        .traceId(traceId)
        .spanId(spanId)
        .parentSpanId(parentSpanId)
        .service(service)
        .level(level)
        .durationMs(durationMs)
        .payloadJson(payloadJson)
        .loggedAt(loggedAt)
        .consumedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")))
        .build();
  }
}
