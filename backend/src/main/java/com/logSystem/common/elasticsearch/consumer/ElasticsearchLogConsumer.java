package com.logSystem.common.elasticsearch.consumer;

import com.logSystem.common.elasticsearch.service.ElasticsearchIndexService;
import com.logSystem.common.kafka.LogTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Elasticsearch 저장 전용 Kafka Consumer.
 *
 * <p>MySQL Consumer({@code log-consumer-group})와 동일한 4개 토픽을 구독하되,
 * 별도 컨슈머 그룹({@code log-es-consumer-group})으로 독립적인 오프셋을 관리한다.
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>배치 레코드 수신 (최대 50건/poll)
 *   <li>{@link ElasticsearchIndexService}로 logType별 Bulk 인덱싱 위임
 *   <li>성공 시 수동 오프셋 커밋 ({@link Acknowledgment#acknowledge()})
 *   <li>실패 시 예외 전파 → {@code DefaultErrorHandler} 재시도 → DLQ 발행
 * </ol>
 *
 * <p>장애 격리: ES 장애가 발생해도 이 Consumer의 오프셋만 멈추고
 * MySQL Consumer는 영향 없이 계속 처리된다.
 *
 * @author Yuri-JUNG
 */
@Component
public class ElasticsearchLogConsumer {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchLogConsumer.class);

  private final ElasticsearchIndexService indexService;

  public ElasticsearchLogConsumer(ElasticsearchIndexService indexService) {
    this.indexService = indexService;
  }

  /**
   * 로그 토픽 배치 메시지를 소비하여 Elasticsearch에 Bulk 인덱싱한다.
   *
   * <p>인덱싱 실패 시 예외를 던져 {@code esKafkaListenerContainerFactory}에 설정된
   * {@code DefaultErrorHandler}가 재시도 및 DLQ 발행을 담당하도록 위임한다.
   *
   * @param records Kafka ConsumerRecord 배치 (최대 50건)
   * @param ack     수동 오프셋 커밋 핸들러
   */
  @KafkaListener(
      topics = {
          LogTopic.API_TOPIC,
          LogTopic.DB_TOPIC,
          LogTopic.EXTERNAL_API_TOPIC,
          LogTopic.ERROR_TOPIC
      },
      groupId          = "log-es-consumer-group",
      containerFactory = "esKafkaListenerContainerFactory"
  )
  public void consume(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
    log.debug("ES Bulk 인덱싱 시작 [batch={}건]", records.size());

    indexService.bulkIndex(records);  // 실패 시 예외 → error handler 위임

    ack.acknowledge();
    log.debug("ES Bulk 인덱싱 완료 및 오프셋 커밋 [batch={}건]", records.size());
  }
}
