package com.logSystem.log.service;

import com.logSystem.log.domain.Log;
import com.logSystem.log.dto.LogRequestDto;
import com.logSystem.log.dto.LogResponseDto;
import com.logSystem.log.repository.LogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LogService {

    private final LogMapper logMapper;

    public void createLog(LogRequestDto requestDto) {
        Log log = Log.builder()
                .level(requestDto.getLevel())
                .message(requestDto.getMessage())
                .source(requestDto.getSource())
                .createdAt(LocalDateTime.now())
                .build();
        logMapper.insertLog(log);
    }

    public List<LogResponseDto> getAllLogs() {
        return logMapper.findAllLogs().stream()
                .map(log -> LogResponseDto.builder()
                        .id(log.getId())
                        .level(log.getLevel())
                        .message(log.getMessage())
                        .source(log.getSource())
                        .createdAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
