package com.logSystem.log.domain.payload;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.logSystem.log.domain.LogType;

/**
 * 로그 타입별 페이로드의 공통 sealed interface.
 *
 * <p>직렬화: {@code logType} 필드가 JSON에 포함되어 역직렬화 시 구현체를 식별한다.
 * <pre>
 * {
 *   "logType": "API",
 *   "http": { ... },
 *   ...
 * }
 * </pre>
 *
 * <p>역직렬화: Jackson이 {@code logType} 값을 읽어 {@link JsonSubTypes}에 등록된
 * 구현 record 중 하나로 매핑한다.
 */
@JsonTypeInfo(
        use     = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,  // logType은 이미 record 컴포넌트로 존재
        property = "logType",
        visible  = true                               // 역직렬화 시 logType 값을 생성자에도 전달
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ApiLogPayload.class,         name = "API"),
        @JsonSubTypes.Type(value = DbLogPayload.class,          name = "DB"),
        @JsonSubTypes.Type(value = ExternalApiLogPayload.class, name = "EXTERNAL_API"),
        @JsonSubTypes.Type(value = ErrorLogPayload.class,       name = "ERROR")
})
public sealed interface LogPayload
        permits ApiLogPayload, DbLogPayload, ExternalApiLogPayload, ErrorLogPayload {

    /** 구현체 타입 식별자. Jackson의 타입 discriminator와 동일한 값을 반환한다. */
    LogType logType();
}
