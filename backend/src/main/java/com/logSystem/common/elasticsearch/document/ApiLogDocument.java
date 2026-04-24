package com.logSystem.common.elasticsearch.document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * 인입 HTTP 요청/응답 로그 인덱스 Document.
 *
 * <p>인덱스명 {@code log-api}는 Kafka 토픽명과 일치한다.
 * Kibana에서 {@code log-api*} 인덱스 패턴으로 조회 가능.
 *
 * <p>한국어 검색 확장: nori 플러그인 설치 후 {@code log_text_analyzer}를
 * {@code nori_tokenizer} 기반 커스텀 분석기로 교체 가능
 * ({@code elasticsearch-plugin install analysis-nori}).
 *
 * @author Yuri-JUNG
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@Document(indexName = "log-api")
@Setting(settingPath = "/elasticsearch/settings.json")
public class ApiLogDocument extends BaseDocument {

  /** GET | POST | PUT | PATCH | DELETE */
  @Field(type = FieldType.Keyword)
  private String method;

  /** 경로 파라미터 템플릿화 URI (e.g. /api/logs/{id}) */
  @Field(type = FieldType.Keyword)
  private String path;

  /** 실제 요청 URI (e.g. /api/logs/42) */
  @Field(type = FieldType.Keyword)
  private String pathRaw;

  /** 쿼리스트링 원문 */
  @Field(type = FieldType.Keyword)
  private String query;

  /** HTTP 응답 코드 — 4xx/5xx 오류 필터링에 사용 */
  @Field(type = FieldType.Integer)
  private Integer statusCode;

  /** X-Forwarded-For 우선 적용된 클라이언트 IP */
  @Field(type = FieldType.Keyword)
  private String clientIp;

  /** 클라이언트 User-Agent — text 분석으로 브라우저/봇 구분 가능 */
  @Field(type = FieldType.Text, analyzer = "standard")
  private String userAgent;
}
