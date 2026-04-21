# ──────────────────────────────────────────────────────────────────────────
# Stage 1: Build
# ──────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# 의존성 레이어를 먼저 캐시 — 소스 변경 시 재다운로드 방지
COPY gradlew gradlew
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q

# 소스 빌드
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test -q

# ──────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime  (JRE only — JDK 제외로 이미지 크기 절감)
# ──────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# health check용 curl 설치
RUN apk add --no-cache curl

COPY --from=builder /app/build/libs/logSystem-*.jar app.jar

# 컨테이너 메모리 제한을 JVM이 자동으로 인식
# MaxRAMPercentage=75 → 컨테이너 mem_limit의 75%를 힙으로 사용
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=100 \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
