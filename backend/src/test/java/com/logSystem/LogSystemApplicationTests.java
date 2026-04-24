package com.logSystem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"log-api", "log-db", "log-external", "log-error"})
class LogSystemApplicationTests {

    /** ES 미실행 환경(CI, 로컬 테스트)에서 실제 연결 없이 컨텍스트 로딩을 통과시킨다. */
    @MockitoBean
    ElasticsearchOperations elasticsearchOperations;

    @Test
    void contextLoads() {
    }

}
