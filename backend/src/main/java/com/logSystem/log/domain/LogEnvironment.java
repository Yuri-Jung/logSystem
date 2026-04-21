package com.logSystem.log.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum LogEnvironment {
    LOCAL, DEV, STAGING, PROD;

    /** JSON 역직렬화 시 대소문자 무관하게 허용 (e.g. "local" → LOCAL) */
    @JsonCreator
    public static LogEnvironment from(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
