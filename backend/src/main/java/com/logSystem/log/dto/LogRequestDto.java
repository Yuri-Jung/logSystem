package com.logSystem.log.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogRequestDto {
    private String level;
    private String message;
    private String source;
}
