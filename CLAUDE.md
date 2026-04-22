# CLAUDE.md

이 파일은 Claude Code(claude.ai/code)가 이 저장소에서 작업할 때 참고하는 가이드입니다.

## 프로젝트 개요

Spring Boot 백엔드와 React 프론트엔드로 구성된 로그 관리 시스템입니다. 백엔드는 REST API로 로그를 수집·저장·분석하고, 프론트엔드는 이를 시각화합니다.

## 명령어

### 백엔드 (`backend/` 디렉토리에서 실행)
```bash
./gradlew.bat bootRun      # 개발 서버 시작 (:8080)
./gradlew.bat test         # 테스트 실행
./gradlew.bat build        # JAR 빌드
```

### 프론트엔드 (`frontend/` 디렉토리에서 실행)
```bash
npm install        # 의존성 설치
npm run dev        # 개발 서버 시작 (:5173)
npm run build      # 프로덕션 빌드
npm run lint       # ESLint 검사
```

## 환경 설정

`backend/.env` 파일 생성 (커밋 금지):
```
DEV_DB_URL=jdbc:mysql://127.0.0.1:3306/logdb?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true
DEV_DB_USERNAME=root
DEV_DB_PASSWORD=password
```

백엔드는 기본적으로 `dev` 프로파일로 실행됩니다. spring-dotenv가 `.env`를 Spring 프로퍼티로 로드하고, `application-dev.yml`이 `${DEV_DB_URL}` 형태로 참조합니다.

## 아키텍처

### 백엔드 (Java 17, Spring Boot, MyBatis, MySQL)

`backend/src/main/java/com/logSystem/` 하위 계층형 구조:
- `log/` — 로그 CRUD 도메인: Controller → Service → LogMapper (인터페이스) → LogMapper.xml (SQL)
- `analysis/` — 로그 통계 도메인: Controller → AnalysisService (TODO 구현 필요)
- `scheduler/LogScheduler.java` — 매일 자정 로그 정리 스케줄러 (TODO)
- `common/config/` — WebConfig (CORS·인터셉터 플레이스홀더), SchedulerConfig
- `common/exception/GlobalExceptionHandler.java` — 전역 예외 처리

`Log` 엔티티는 `logdb.log` 테이블(`id`, `level`, `message`, `source`, `created_at`)에 매핑됩니다.

MyBatis 매퍼 인터페이스 `LogMapper.java`는 `mapper/log/LogMapper.xml`로 구현되며, 타입 별칭과 매퍼 경로는 `application.yml`에 설정되어 있습니다.

### 프론트엔드 (React 18, Vite, Tailwind CSS, Axios)

- `src/services/api.js` — `baseURL: '/api'`로 설정된 Axios 인스턴스
- Vite 개발 프록시: `/api/*` 요청을 `http://localhost:8080`으로 전달
- `App.jsx`가 루트 컴포넌트이며, 페이지·컴포넌트는 `src/` 하위에 위치

### API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/test` | 헬스 체크 |
| POST | `/api/logs` | 로그 생성 |
| GET | `/api/logs` | 전체 로그 조회 |
| GET | `/api/analysis` | 로그 통계 조회 |

## Guidelines

### 언어 규칙
- 모든 코드 설명, 주석, 문서는 **한국어**로 작성한다.

### 주석 규칙 (JavaDoc 표준)
- 모든 public 클래스·메서드·필드에는 JavaDoc 주석을 한국어로 작성한다.
- JavaDoc 기본 구조를 준수한다:
  ```java
  /**
   * 메서드 또는 클래스의 역할을 한 줄로 요약한다.
   *
   * <p>필요한 경우 상세 설명을 추가한다.
   *
   * @param paramName 파라미터 설명
   * @return 반환값 설명
   * @throws ExceptionType 예외 발생 조건
   */
  ```
- 인라인 주석(`//`)은 코드만으로 WHY를 설명할 수 없는 경우에만 사용한다.
- `@author`, `@version`, `@since` 태그는 클래스 레벨 JavaDoc에 작성한다.

### Google Java Style Guide
- **들여쓰기**: 공백 2칸 (탭 금지)
- **줄 길이**: 최대 100자
- **중괄호**: K&R 스타일 — 여는 중괄호는 같은 줄에 위치
  ```java
  // 올바른 예
  if (condition) {
    doSomething();
  }
  ```
- **네이밍 컨벤션**:
  - 클래스: `UpperCamelCase`
  - 메서드·변수: `lowerCamelCase`
  - 상수: `UPPER_SNAKE_CASE`
  - 패키지: `lowercase` (언더스코어 금지)
- **임포트**: 와일드카드 임포트(`import java.util.*`) 금지, 사용하는 클래스만 명시적으로 임포트
- **빈 줄**: 논리적으로 관련 없는 코드 블록 사이에만 삽입, 과도한 빈 줄 금지
- **Annotations**: 클래스·메서드 선언 바로 위에 각각 한 줄씩 작성
  ```java
  @Override
  @Nullable
  public String findNameById(Long id) { ... }
  ```

### 클린 코드 원칙 (대기업 기술 면접 기준)

**단일 책임 원칙 (SRP)**
- 클래스와 메서드는 하나의 역할만 담당한다.
- 메서드는 20줄 이내를 목표로 한다.

**명확한 네이밍**
- 변수·메서드·클래스 이름만으로 의도가 전달되어야 한다.
- 축약어 사용을 지양하고, 도메인 언어를 반영한 이름을 사용한다.

**주석 최소화**
- 코드 자체로 설명되어야 하며, WHY가 비자명한 경우에만 짧은 주석을 허용한다.

**SOLID 원칙 준수**
- 계층 간 의존성 방향 엄수: Controller → Service → Repository
- 인터페이스를 통한 의존성 역전(DIP) 적용

**DRY (중복 제거)**
- 동일한 로직이 두 곳 이상에 있으면 공통화한다.

**계층 분리**
- DTO와 도메인 객체를 명확히 분리하고, 계층 간 변환 책임을 서비스 레이어에 위임한다.

**예외 처리**
- 비즈니스 예외는 커스텀 예외 클래스로 정의하고, `GlobalExceptionHandler`에서 일관되게 처리한다.
- 포괄적인 `Exception` catch는 지양하고 구체적인 예외 타입을 사용한다.
