package com.logSystem.analysis.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AnalysisResponseDto {
    private String level;
    private long count;
}
