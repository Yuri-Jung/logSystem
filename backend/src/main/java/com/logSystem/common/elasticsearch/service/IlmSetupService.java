package com.logSystem.common.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.EmptyObject;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.ilm.Actions;
import co.elastic.clients.elasticsearch.ilm.ForceMergeAction;
import co.elastic.clients.elasticsearch.ilm.IlmPolicy;
import co.elastic.clients.elasticsearch.ilm.Phase;
import co.elastic.clients.elasticsearch.ilm.Phases;
import co.elastic.clients.elasticsearch.ilm.RolloverAction;
import co.elastic.clients.elasticsearch.ilm.SetPriorityAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Elasticsearch ILM(Index Lifecycle Management) 및 인덱스 템플릿 초기화 서비스.
 *
 * <p>애플리케이션 시작 시 아래 세 가지를 순서대로 자동 적용한다:
 * <ol>
 *   <li>{@code log-ilm-policy}: Hot(0~7일) → Warm(7~14일) → Cold(14~30일) → Delete(30일+) 4단계 정책
 *   <li>{@code log-index-template}: {@code log-*} 패턴 공통 인덱스 템플릿 등록 (ILM 정책·필드 매핑 포함)
 *   <li>기존 4개 인덱스(log-api, log-db, log-external, log-error)에 정책 직접 연결
 * </ol>
 *
 * <p>{@link Order}(1)로 {@link ElasticsearchIndexService}(Order 2) 보다 먼저 실행되어
 * 인덱스 생성 전에 템플릿이 반드시 등록된다.
 *
 * @author Yuri-JUNG
 */
@Service
public class IlmSetupService {

  private static final Logger log = LoggerFactory.getLogger(IlmSetupService.class);

  /** ILM 정책 이름 — 인덱스 설정에서 {@code index.lifecycle.name} 값으로 사용 */
  static final String POLICY_NAME = "log-ilm-policy";

  /** 인덱스 템플릿 이름 — {@code log-*} 패턴 공통 인덱스 템플릿 */
  private static final String TEMPLATE_NAME = "log-index-template";

  /** ILM 정책을 연결할 대상 인덱스 이름 */
  private static final List<String> DATA_STREAM_NAMES =
      List.of("log-api", "log-db", "log-external", "log-error");

  private final ElasticsearchClient esClient;

  public IlmSetupService(ElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  /**
   * 애플리케이션 준비 완료 후 ILM 정책·인덱스 템플릿을 등록하고 기존 인덱스에 정책을 연결한다.
   *
   * <p>ES가 미연결 상태이거나 이미 정책/템플릿이 존재하는 경우 경고 로그만 남기고 계속 진행한다.
   * {@link Order}(1): 인덱스 초기화({@link ElasticsearchIndexService}) 보다 먼저 실행된다.
   */
  @Order(1)
  @EventListener(ApplicationReadyEvent.class)
  public void setup() {
    try {
      applyIlmPolicy();
      applyIndexTemplate();
      linkPolicyToExistingIndices();
    } catch (Exception e) {
      log.warn("ILM/템플릿 설정 실패 (ES 미연결 상태일 수 있음): {}", e.getMessage());
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // ILM 정책 생성
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Hot-Warm-Cold-Delete 4단계 ILM 정책을 생성(또는 갱신)한다.
   *
   * <p>정책이 이미 존재하면 덮어쓴다(ES PUT 의미론 = upsert).
   * 각 단계 전환 시각은 롤오버 기준이 아닌 인덱스 생성 시각 기준이다.
   */
  private void applyIlmPolicy() throws IOException {
    IlmPolicy policy = IlmPolicy.of(p -> p.phases(buildPhases()));

    esClient.ilm().putLifecycle(r -> r.name(POLICY_NAME).policy(policy));
    log.info("ILM 정책 적용 완료: {}", POLICY_NAME);
  }

  /**
   * 4단계 정책(hot → warm → cold → delete)을 구성한다.
   *
   * <pre>
   *  Hot  (0 ~ 7d)   : 롤오버(50GB 또는 7일), 높은 우선순위
   *  Warm (7 ~ 14d)  : Forcemerge(세그먼트 1개 병합), 중간 우선순위
   *  Cold (14 ~ 30d) : 읽기 전용, 낮은 우선순위 (저렴한 스토리지 tier 이동 가능)
   *  Delete (30d+)   : 인덱스 자동 삭제
   * </pre>
   */
  private Phases buildPhases() {
    return Phases.of(p -> p
        .hot(buildHotPhase())
        .warm(buildWarmPhase())
        .cold(buildColdPhase())
        .delete(buildDeletePhase())
    );
  }

  /**
   * Hot 단계: 7일 또는 50GB 초과 시 새 backing index로 롤오버.
   *
   * <p>롤오버 조건을 낮추면 인덱스가 더 자주 생성되어 파일 수가 늘지만,
   * 각 인덱스의 크기가 줄어 Warm 전환이 빨라진다.
   */
  private Phase buildHotPhase() {
    return Phase.of(p -> p
        .minAge(Time.of(t -> t.time("0ms")))
        .actions(Actions.of(a -> a
            .rollover(RolloverAction.of(r -> r
                .maxAge(Time.of(t -> t.time("7d")))
                .maxPrimaryShardSize("50gb")
            ))
            .setPriority(SetPriorityAction.of(s -> s.priority(100)))
        ))
    );
  }

  /**
   * Warm 단계: 롤오버 7일 후 세그먼트를 1개로 병합하여 검색 성능 유지.
   *
   * <p>Forcemerge 완료 후에는 인덱스가 읽기 전용이 된다.
   * 멀티 노드 환경에서는 여기에 {@code allocate} 액션을 추가하여
   * warm 노드로 샤드를 이동할 수 있다.
   */
  private Phase buildWarmPhase() {
    return Phase.of(p -> p
        .minAge(Time.of(t -> t.time("7d")))
        .actions(Actions.of(a -> a
            .forcemerge(ForceMergeAction.of(fm -> fm.maxNumSegments(1)))
            .setPriority(SetPriorityAction.of(s -> s.priority(50)))
        ))
    );
  }

  /**
   * Cold 단계: 롤오버 14일 후 읽기 전용으로 전환.
   *
   * <p>멀티 노드 환경에서는 {@code allocate} 또는 {@code migrate} 액션으로
   * cold 전용 노드(HDD, 저렴한 스토리지)로 샤드를 이동한다.
   */
  private Phase buildColdPhase() {
    return Phase.of(p -> p
        .minAge(Time.of(t -> t.time("14d")))
        .actions(Actions.of(a -> a
            .readonly(EmptyObject._INSTANCE)
            .setPriority(SetPriorityAction.of(s -> s.priority(0)))
        ))
    );
  }

  /** Delete 단계: 롤오버 30일 후 인덱스를 자동으로 삭제한다. */
  private Phase buildDeletePhase() {
    return Phase.of(p -> p
        .minAge(Time.of(t -> t.time("30d")))
        .actions(Actions.of(a -> a.delete(d -> d)))
    );
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 인덱스 템플릿 등록
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * {@code log-index-template.json}을 읽어 인덱스 템플릿을 등록(또는 갱신)한다.
   *
   * <p>{@code log-*} 패턴에 일치하는 모든 인덱스에 ILM 정책과 공통 매핑이 자동 적용된다.
   * 템플릿 파일이 클래스패스에 없으면 경고 로그만 남기고 건너뛴다.
   *
   * @throws IOException ES API 호출 실패 시
   */
  private void applyIndexTemplate() throws IOException {
    try (InputStream is = getClass().getResourceAsStream("/elasticsearch/log-index-template.json")) {
      if (is == null) {
        log.warn("인덱스 템플릿 파일 없음: /elasticsearch/log-index-template.json");
        return;
      }
      esClient.indices().putIndexTemplate(r -> r.name(TEMPLATE_NAME).withJson(is));
    }
    log.info("인덱스 템플릿 적용 완료: {}", TEMPLATE_NAME);
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 기존 인덱스 연결
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * 이미 생성된 4개 인덱스에 ILM 정책을 연결한다.
   *
   * <p>인덱스가 없는 경우 조용히 건너뛴다.
   */
  private void linkPolicyToExistingIndices() {
    for (String index : DATA_STREAM_NAMES) {
      try {
        esClient.indices().putSettings(r -> r
            .index(index)
            .settings(s -> s.lifecycle(lc -> lc.name(POLICY_NAME)))
        );
        log.debug("기존 인덱스 ILM 연결 완료: {}", index);
      } catch (Exception e) {
        log.debug("ILM 연결 건너뜀 [index={}]: {}", index, e.getMessage());
      }
    }
  }
}
