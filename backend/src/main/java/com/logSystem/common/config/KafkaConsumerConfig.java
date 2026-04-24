package com.logSystem.common.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer 설정.
 *
 * <p>수동 오프셋 커밋(MANUAL_IMMEDIATE)으로 메시지 유실을 방지하고,
 * 지수 백오프 재시도로 일시적 장애를 흡수한다.
 *
 * @author Yuri-JUNG
 */
@EnableKafka
@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaConsumerConfig {

  private static final String GROUP_ID         = "log-consumer-group";
  private static final long   BACKOFF_INITIAL  = 1_000L;  // 첫 재시도 대기 1초
  private static final long   BACKOFF_MAX      = 16_000L; // 최대 대기 16초
  private static final long   BACKOFF_MAX_ATTEMPTS = 4;   // 최초 1회 + 재시도 3회

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  /**
   * Kafka Consumer 연결 속성을 정의하는 팩토리.
   *
   * <p>KEY/VALUE 모두 String으로 받아 ObjectMapper에서 직접 역직렬화한다.
   * auto-commit 비활성화로 처리 완료 후에만 오프셋을 커밋한다.
   */
  @Bean
  public ConsumerFactory<String, String> consumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
    props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 60_000);
    return new DefaultKafkaConsumerFactory<>(props);
  }

  /**
   * @KafkaListener 가 사용할 컨테이너 팩토리.
   *
   * <p>에러 핸들러 설계 원칙:
   * <ul>
   *   <li>역직렬화 실패(JsonParseException): 재시도 불가 → 즉시 오프셋 커밋 후 건너뜀
   *   <li>DB 저장 실패(DataAccessException): 재시도 가능 → 지수 백오프 후 재처리
   *   <li>최대 재시도 초과: 에러 로그만 남기고 오프셋 커밋 (데드레터 큐 미도입)
   * </ul>
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> consumerFactory) {

    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(consumerFactory);
    // 존재하지 않는 토픽 구독 시 컨테이너 시작 실패 방지
    factory.setMissingTopicsFatal(false);

    // 처리 완료 후 Acknowledgment.acknowledge() 호출 시점에 커밋
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

    // 지수 백오프: 1s → 2s → 4s → 8s (최대 3회 재시도)
    ExponentialBackOff backOff = new ExponentialBackOff(BACKOFF_INITIAL, 2.0);
    backOff.setMaxElapsedTime(BACKOFF_MAX);
    backOff.setMaxAttempts(BACKOFF_MAX_ATTEMPTS);

    factory.setCommonErrorHandler(new DefaultErrorHandler(backOff));

    return factory;
  }
}
