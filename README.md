# LogSystem Project

Spring Boot 기반의 백엔드와 React 기반의 프론트엔드로 구성된 로그 관리 및 분석 시스템 프로젝트입니다.

## 개발 환경

### Backend (Spring Boot)
- **Java**: 17
- **Framework**: Spring Boot 4.x
- **Build Tool**: Gradle
- **Database**: MySQL
- **ORM**: MyBatis
- **Lombok**: 적용됨

### Frontend (React)
- **Framework**: React 19
- **Build Tool**: Vite
- **Styling**: Tailwind CSS v4
- **HTTP Client**: Axios

---

## 프로젝트 구조

```text
logSystem/
├── backend/                # Spring Boot 백엔드 프로젝트
│   ├── src/main/java/...   # 서버 소스 코드
│   │   ├── common          # 공통 기능 (설정, 예외 처리)
│   │   ├── log             # 로그 데이터 수집 및 관리 도메인
│   │   ├── analysis        # 로그 분석 및 통계 도메인
│   │   └── scheduler       # 스케줄러 컴포넌트
│   ├── build.gradle        # 의존성 설정
│   └── .env                # 서버 환경변수 (DB 접속 정보 등)
│
└── frontend/               # React 프론트엔드 프로젝트
    ├── src/
    │   ├── assets          # 정적 파일 (이미지, 폰트 등)
    │   ├── components      # 공통 UI 컴포넌트
    │   ├── context         # 전역 상태 관리
    │   ├── hooks           # Custom Hooks
    │   ├── pages           # 라우팅 페이지
    │   ├── services        # API 호출 (Axios 인스턴스)
    │   └── utils           # 유틸리티 함수
    ├── vite.config.js      # Vite, Tailwind, Proxy 설정
    └── package.json        # NPM 패키지 설정
```

---

## 백엔드 (Backend) 설정 및 실행 방법

1. **MySQL 설정**
   - 로컬 환경에 MySQL을 설치 및 실행합니다.
   - `logdb` 스키마(데이터베이스)를 생성합니다.

2. **환경변수(`.env`) 설정**
   - `backend` 폴더 최상단에 `.env` 파일을 생성하고 다음 내용을 작성합니다. (비밀번호는 본인 환경에 맞게 변경)
     ```env
     # Local (Dev) Environment
     DEV_DB_URL=jdbc:mysql://127.0.0.1:3306/logdb?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true
     DEV_DB_USERNAME=root
     DEV_DB_PASSWORD=password
     
     # Production Environment
     PROD_DB_URL=jdbc:mysql://prod-db-host:3306/logdb?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&useSSL=false
     PROD_DB_USERNAME=prod_user
     PROD_DB_PASSWORD=prod_password
     ```

3. **서버 실행**
   - `backend` 폴더로 이동하여 서버를 실행합니다.
   - 기본적으로 `dev` 프로파일이 활성화됩니다.
     ```bash
     cd backend
     ./gradlew bootRun   # Mac / Linux
     # 또는
     ./gradlew.bat bootRun # Windows
     ```

4. **테스트**
   - 브라우저 접속(Get방식): `http://localhost:8080/api/test`
   - 응답: `LogSystem Application is running!`

---

## 프론트엔드 (Frontend) 설정 및 실행 방법

1. **패키지 설치**
   - Node.js 환경이 필요합니다.
   - `frontend` 폴더로 이동하여 패키지를 설치합니다.
     ```bash
     cd frontend
     npm install
     ```

2. **개발 서버 실행**
   - 개발 서버를 구동합니다.
     ```bash
     npm run dev
     ```

3. **화면 접속 및 API 연동 테스트**
   - 브라우저 접속: `http://localhost:5173`
   - 화면에 백엔드 API로부터 응답받은 메시지가 정상적으로 표시되는지 확인합니다.
   - (Vite의 Proxy 설정을 통해 프론트엔드의 `/api` 요청이 자동으로 백엔드 `http://localhost:8080`으로 포워딩됩니다.)
