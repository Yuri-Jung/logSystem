package com.logSystem.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LogScheduler {

    // 매일 자정에 실행 (예시)
    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanupOldLogs() {
        log.info("오래된 로그 정리 스케줄러 실행"); 
        // TODO: 구현 필요
    }
}
