# LogSystem Project

Spring Boot 기반의 로그 관리 시스템 프로젝트입니다.

## 개발 환경
- **Java**: 17
- **Framework**: Spring Boot 4.x
- **Build Tool**: Gradle
- **Database**: MySQL
- **ORM**: MyBatis
- **Lombok**: 적용됨

## 프로젝트 구조
```
com.logSystem
├── common                  # 공통 기능 모음
│   ├── config              # 각종 설정 (WebConfig, SchedulerConfig 등)
│   └── exception           # 예외 처리 (GlobalExceptionHandler)
├── log                     # 로그 데이터 수집 및 관리 도메인
│   ├── controller          # API 엔드포인트 (LogController)
│   ├── service             # 비즈니스 로직 (LogService)
│   ├── repository          # DB 접근 (LogMapper - MyBatis)
│   ├── domain              # 엔티티 모델 (Log)
│   └── dto                 # 데이터 전송 객체 (LogRequestDto, LogResponseDto)
├── analysis                # 로그 데이터 분석 및 통계 도메인
│   ├── controller          # API 엔드포인트 (AnalysisController)
│   ├── service             # 비즈니스 로직 (AnalysisService)
│   └── dto                 # 데이터 전송 객체 (AnalysisResponseDto)
└── scheduler               # 주기적인 작업(스케줄링) 관리
    └── LogScheduler        # 스케줄러 컴포넌트
```

## 실행 방법
1. 로컬 환경에 MySQL을 설치하고 실행합니다.
2. `src/main/resources/application.yml` 파일에서 MySQL 데이터베이스 접속 정보를 환경에 맞게 수정합니다 (기본 스키마: `logdb`).
3. 아래의 명령어로 빌드 및 실행합니다.

```bash
# Windows
./gradlew.bat bootRun

# Mac / Linux
./gradlew bootRun
```

## 테스트
브라우저 또는 터미널을 열고 다음 URL로 접속하여 서버가 정상 동작하는지 확인합니다.
- URL: `http://localhost:8080/api/test`
- 응답: `LogSystem Application is running!`
