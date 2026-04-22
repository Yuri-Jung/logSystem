package com.logSystem.common.kafka;

import com.logSystem.log.domain.LogType;

public enum LogTopic {

    API("log-api"),
    DB("log-db"),
    EXTERNAL_API("log-external"),
    ERROR("log-error");

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
