# Slack App

Slack 클론 코딩 프로젝트 (학습 목적)

## 기술 스택

- **Backend**: Java 17 + Spring Boot 3.2 + Gradle
- **Frontend**: Next.js 14 + TypeScript
- **Database**: PostgreSQL

## 시작하기

### Prerequisites

- Java 17+
- Node.js 18+
- Docker & Docker Compose

### Backend 실행

```bash
cd backend
./gradlew bootRun
```

### Frontend 실행

```bash
cd frontend
npm install
npm run dev
```

### Database 실행

```bash
docker-compose up -d postgres
```

## 프로젝트 구조

```
slack/
├── backend/          # Spring Boot 백엔드
├── frontend/         # Next.js 프론트엔드
├── docker-compose.yml
└── README.md
```

## 개발 로드맵

자세한 개발 계획은 `local/ROADMAP.md` 파일을 참고하세요.
(local 디렉토리는 git으로 관리되지 않습니다)

