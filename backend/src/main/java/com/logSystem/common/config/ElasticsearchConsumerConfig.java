package com.logSystem.common.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Elasticsearch Consumer 전용 Kafka 설정.
 *
 * <p>MySQL Consumer({@code log-consumer-group})와 완전히 분리된 컨슈머 그룹
 * ({@code log-es-consumer-group})으로 동일 토픽을 구독한다.
 * 두 컨슈머는 독립적인 오프셋을 유지하므로 한쪽 장애가 다른 쪽에 영향을 주지 않는다.
 *
 * <p>실패 처리 전략:
 * <ul>
 *   <li>지수 백오프(1s → 2s → 4s)로 최대 3회 재시도
 *   <li>재시도 초과 시 {@code log-es-dlq} 토픽으로 발행 (DLQ)
 * </ul>
 *
 * @author Yuri-JUNG
 */
@EnableKafka
@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class ElasticsearchConsumerConfig {

  private static final String ES_GROUP_ID = "log-es-consumer-group";
  private static final String DLQ_TOPIC   = "log-es-dlq";
  private static final int    BATCH_SIZE  = 50;

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  /**
   * Elasticsearch Consumer 전용 {@link ConcurrentKafkaListenerContainerFactory}.
   *
   * <p>배치 리스너({@code setBatchListener(true)})로 한 번의 poll에서 최대 {@value #BATCH_SIZE}건을
   * 읽어 Bulk 인덱싱에 전달한다.
   *
   * @param kafkaTemplate DLQ 발행에 사용할 KafkaTemplate
   * @return Elasticsearch Consumer 전용 컨테이너 팩토리
   */
  @Bean("esKafkaListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<String, String> esKafkaListenerContainerFactory(
      KafkaTemplate<String, String> kafkaTemplate) {

    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(buildConsumerProps()));
    factory.setBatchListener(true);
    factory.setMissingTopicsFatal(false);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    factory.setCommonErrorHandler(buildErrorHandler(kafkaTemplate));

    return factory;
  }

  private Map<String, Object> buildConsumerProps() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,    bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG,             ES_GROUP_ID);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,    "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,   false);
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,     BATCH_SIZE);
    props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 120_000);
    return props;
  }

  /**
   * 지수 백오프 재시도 후 DLQ로 분리하는 에러 핸들러.
   *
   * <p>재시도 횟수 초과 시 {@value #DLQ_TOPIC} 토픽으로 원본 메시지를 발행한다.
   * DLQ 토픽은 별도 Consumer로 수동 재처리하거나 모니터링 알림에 활용한다.
   */
  private DefaultErrorHandler buildErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
        kafkaTemplate,
        (record, ex) -> new TopicPartition(DLQ_TOPIC, 0)
    );

    ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
    backOff.setMaxElapsedTime(8_000L);
    backOff.setMaxAttempts(3);

    return new DefaultErrorHandler(recoverer, backOff);
  }
}
