package com.logSystem.log.controller;

import com.logSystem.log.dto.LogRequestDto;
import com.logSystem.log.dto.LogResponseDto;
import com.logSystem.log.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @PostMapping
    public ResponseEntity<Void> createLog(@RequestBody LogRequestDto requestDto) {
        logService.createLog(requestDto);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<LogResponseDto>> getLogs() {
        List<LogResponseDto> logs = logService.getAllLogs();
        return ResponseEntity.ok(logs);
    }
}
