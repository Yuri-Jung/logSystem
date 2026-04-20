package com.logSystem.log.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Log {
    private Long id;
    private String level;
    private String message;
    private String source;
    private LocalDateTime createdAt;
}
