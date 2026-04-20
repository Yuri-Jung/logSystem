package com.logSystem.log.repository;

import com.logSystem.log.domain.Log;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface LogMapper {
    void insertLog(Log log);
    List<Log> findAllLogs();
    Log findLogById(Long id);
}
