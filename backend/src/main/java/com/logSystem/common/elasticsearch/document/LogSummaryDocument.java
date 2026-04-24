package com.logSystem.common.elasticsearch.document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.elasticsearch.annotations.Document;

/**
 * 크로스 인덱스 검색 전용 읽기 전용 Document.
 *
 * <p>4개 인덱스(log-api, log-db, log-external, log-error)를 단일 쿼리로 조회할 때
 * 공통 필드({@link BaseDocument})만 매핑 대상으로 삼는다.
 * 타입별 상세 필드(method, query, stackTrace 등)는 목록 조회에서 불필요하므로 제외한다.
 *
 * <p>주의: 이 클래스는 쓰기(인덱싱)에 사용하지 않는다.
 * {@code createIndex = false}로 ES 인덱스 자동 생성을 비활성화한다.
 *
 * @author Yuri-JUNG
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@Document(indexName = "log-api", createIndex = false)
public class LogSummaryDocument extends BaseDocument {
  // BaseDocument의 공통 필드만 사용 — 추가 필드 없음
}
