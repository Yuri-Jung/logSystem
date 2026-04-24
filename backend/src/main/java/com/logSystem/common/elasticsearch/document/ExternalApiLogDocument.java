package com.logSystem.common.elasticsearch.document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * 아웃바운드 외부 API 호출 로그 인덱스 Document.
 *
 * <p>{@code isTimeout=true} 필터로 타임아웃 발생 패턴을 분석하거나,
 * {@code targetService} 기준 집계로 외부 의존성 품질을 모니터링할 수 있다.
 *
 * @author Yuri-JUNG
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@Document(indexName = "log-external")
@Setting(settingPath = "/elasticsearch/settings.json")
public class ExternalApiLogDocument extends BaseDocument {

  /** 호출 대상 서비스 이름 (e.g. slack-api, payment-service) */
  @Field(type = FieldType.Keyword)
  private String targetService;

  /** 호출한 전체 URL */
  @Field(type = FieldType.Keyword)
  private String url;

  /** GET | POST | PUT | DELETE */
  @Field(type = FieldType.Keyword)
  private String method;

  /** HTTP 응답 코드. 타임아웃/네트워크 오류 시 null */
  @Field(type = FieldType.Integer)
  private Integer statusCode;

  /** 타임아웃 발생 여부 */
  @Field(type = FieldType.Boolean)
  private Boolean isTimeout;

  /** 재시도 횟수 (0 = 최초 시도 성공) */
  @Field(type = FieldType.Integer)
  private Integer retryCount;

  /** CLOSED | OPEN | HALF_OPEN */
  @Field(type = FieldType.Keyword)
  private String circuitBreakerState;
}
