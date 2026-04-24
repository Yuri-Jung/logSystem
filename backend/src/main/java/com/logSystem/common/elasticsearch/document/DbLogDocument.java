package com.logSystem.common.elasticsearch.document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * DB 쿼리 실행 로그 인덱스 Document.
 *
 * <p>{@code isSlow=true} 필터로 슬로우 쿼리만 집계하거나,
 * {@code table} 기준 집계로 테이블별 쿼리 빈도를 분석할 수 있다.
 *
 * @author Yuri-JUNG
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@Document(indexName = "log-db")
@Setting(settingPath = "/elasticsearch/settings.json")
public class DbLogDocument extends BaseDocument {

  /** SELECT | INSERT | UPDATE | DELETE | BATCH */
  @Field(type = FieldType.Keyword)
  private String operation;

  /** MyBatis Mapper 전체 경로 */
  @Field(type = FieldType.Keyword)
  private String mapper;

  /** 주 대상 테이블명 */
  @Field(type = FieldType.Keyword)
  private String table;

  /** 실행된 SQL — 전문 검색(Full-text)으로 특정 쿼리 패턴 탐지 가능 */
  @Field(type = FieldType.Text, analyzer = "standard")
  private String query;

  /** SELECT 결과 행 수 */
  @Field(type = FieldType.Integer)
  private Integer rowCount;

  /** 슬로우 쿼리 여부 (durationMs > 1000) */
  @Field(type = FieldType.Boolean)
  private Boolean isSlow;
}
