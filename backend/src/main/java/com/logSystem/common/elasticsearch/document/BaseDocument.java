package com.logSystem.common.elasticsearch.document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;

/**
 * 모든 로그 인덱스 Document의 공통 기반 클래스.
 *
 * <p>traceId 기반 전체 로그 조회를 위해 4개 인덱스(log-api, log-db, log-external, log-error)가
 * 동일한 공통 필드 구조를 공유한다.
 *
 * <p>필드 매핑 전략:
 * <ul>
 *   <li>traceId / spanId / parentSpanId: {@code keyword} — 정확히 일치 검색, 집계
 *   <li>level / service / environment / host / logType: {@code keyword} — 필터링, 패싯
 *   <li>timestamp: {@code date} — 시계열 범위 검색, Kibana 시각화
 *   <li>durationMs: {@code long} — 슬로우 쿼리/API 범위 필터
 * </ul>
 *
 * <p>document ID 전략: {@code {topic}-{partition}-{offset}} 형식의 결정적 ID를 사용한다.
 * 동일 Kafka 메시지를 재처리하더라도 ES upsert 의미론으로 중복 문서가 생기지 않는다.
 *
 * @author Yuri-JUNG
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class BaseDocument {

  /** Kafka 레코드 위치 기반 결정적 ID: {topic}-{partition}-{offset} */
  @Id
  private String id;

  /** 요청 흐름 전체 식별자 — traceId로 4개 인덱스 cross-index 조회 가능 */
  @Field(type = FieldType.Keyword)
  private String traceId;

  /** 이 로그가 속한 작업 단위 식별자 */
  @Field(type = FieldType.Keyword)
  private String spanId;

  /** 부모 span 식별자. API 최초 진입점은 null */
  @Field(type = FieldType.Keyword)
  private String parentSpanId;

  /** 로그 발생 시각 (UTC). Kibana 시계열 시각화의 기준 필드 */
  @Field(type = FieldType.Date, format = {DateFormat.date_time})
  private Instant timestamp;

  /** INFO | WARN | ERROR */
  @Field(type = FieldType.Keyword)
  private String level;

  /** 로그를 발생시킨 서비스명 */
  @Field(type = FieldType.Keyword)
  private String service;

  /** 실행 환경: LOCAL | DEV | PROD */
  @Field(type = FieldType.Keyword)
  private String environment;

  /** 로그를 발생시킨 호스트명 */
  @Field(type = FieldType.Keyword)
  private String host;

  /** API | DB | EXTERNAL_API | ERROR */
  @Field(type = FieldType.Keyword)
  private String logType;

  /** 처리 시간 (ms). 로그 타입에 따라 없을 수 있음 */
  @Field(type = FieldType.Long)
  private Long durationMs;
}
