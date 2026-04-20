package com.logSystem.log.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class LogResponseDto {
    private Long id;
    private String level;
    private String message;
    private String source;
    private LocalDateTime createdAt;
}
