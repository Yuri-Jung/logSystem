package com.logSystem.log.repository;

import com.logSystem.log.domain.SystemLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code system_logs} 테이블 MyBatis 매퍼.
 *
 * @author Yuri-JUNG
 */
@Mapper
public interface SystemLogMapper {

  /**
   * Kafka Consumer가 소비한 로그를 {@code system_logs} 테이블에 저장한다.
   *
   * @param systemLog 저장할 로그 도메인 객체
   */
  void insert(SystemLog systemLog);
}
