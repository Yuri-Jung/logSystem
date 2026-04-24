package com.logSystem.common.elasticsearch.document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * 예외/에러 발생 로그 인덱스 Document.
 *
 * <p>stackTrace는 저장하되 인덱싱하지 않아({@code index = false}) 검색 부하를 줄인다.
 * errorMessage는 전문 검색으로 유사 오류 패턴 탐지에 활용한다.
 *
 * @author Yuri-JUNG
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@Document(indexName = "log-error")
@Setting(settingPath = "/elasticsearch/settings.json")
public class ErrorLogDocument extends BaseDocument {

  /** 도메인 정의 에러 코드 (e.g. ERR_DB_TIMEOUT, ERR_VALIDATION_FAILED) */
  @Field(type = FieldType.Keyword)
  private String errorCode;

  /** 예외 클래스 전체 경로 */
  @Field(type = FieldType.Keyword)
  private String exceptionClass;

  /** 예외 메시지 — 전문 검색으로 유사 오류 탐지 */
  @Field(type = FieldType.Text, analyzer = "standard")
  private String errorMessage;

  /** 스택 트레이스 — 저장만 하고 검색 인덱스에서 제외 (검색 부하 절감) */
  @Field(type = FieldType.Text, index = false)
  private String stackTrace;

  /** 에러 발생 HTTP 메서드 */
  @Field(type = FieldType.Keyword)
  private String httpMethod;

  /** 에러 발생 HTTP 경로 */
  @Field(type = FieldType.Keyword)
  private String httpPath;

  /** 클라이언트에 반환된 HTTP 상태 코드 */
  @Field(type = FieldType.Integer)
  private Integer httpStatus;
}
