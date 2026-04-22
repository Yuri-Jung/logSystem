package com.logSystem.common.kafka;

import com.logSystem.log.domain.LogType;

public enum LogTopic {

    API("log-api"),
    DB("log-db"),
    EXTERNAL_API("log-external"),
    ERROR("log-error");

  /** @KafkaListener topics 속성은 상수 참조만 허용하므로 별도 상수로 노출 */
  public static final String API_TOPIC          = "log-api";
  public static final String DB_TOPIC           = "log-db";
  public static final String EXTERNAL_API_TOPIC = "log-external";
  public static final String ERROR_TOPIC        = "log-error";

    public final String topicName;

    LogTopic(String topicName) {
        this.topicName = topicName;
    }

    public static LogTopic from(LogType logType) {
        return switch (logType) {
            case API           -> API;
            case DB            -> DB;
            case EXTERNAL_API  -> EXTERNAL_API;
            case ERROR         -> ERROR;
        };
    }
}
