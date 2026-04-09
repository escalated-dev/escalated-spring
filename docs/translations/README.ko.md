<p align="center">
  <a href="README.ar.md">العربية</a> •
  <a href="README.de.md">Deutsch</a> •
  <a href="../../README.md">English</a> •
  <a href="README.es.md">Español</a> •
  <a href="README.fr.md">Français</a> •
  <a href="README.it.md">Italiano</a> •
  <a href="README.ja.md">日本語</a> •
  <b>한국어</b> •
  <a href="README.nl.md">Nederlands</a> •
  <a href="README.pl.md">Polski</a> •
  <a href="README.pt-BR.md">Português (BR)</a> •
  <a href="README.ru.md">Русский</a> •
  <a href="README.tr.md">Türkçe</a> •
  <a href="README.zh-CN.md">简体中文</a>
</p>

# Escalated Spring

Spring Boot 애플리케이션을 위한 내장 가능한 헬프데스크 시스템. 단일 종속성으로 모든 Java 애플리케이션에 완전한 기능의 지원 데스크를 추가합니다.

## 기능

1. **Ticket CRUD** -- 상태, 우선순위, 할당을 통한 전체 라이프사이클 관리
2. **SLA Policies** -- 영업 시간 지원 및 휴일 캘린더를 갖춘 구성 가능한 SLA
3. **Automations** -- 해결된 티켓 자동 닫기 및 자동 할당을 위한 시간 기반 규칙
4. **Escalation Rules** -- SLA 위반 시 재할당 및 알림을 통한 자동 에스컬레이션
5. **Macros & Canned Responses** -- 에이전트를 위한 사전 정의된 작업 및 응답 템플릿
6. **Custom Fields** -- 다양한 필드 유형의 확장 가능한 티켓 데이터
7. **Knowledge Base** -- 검색, 조회수, 피드백이 포함된 기사 및 카테고리
8. **Webhooks** -- 재시도 로직을 갖춘 HMAC 서명 Webhook 전달
9. **API Tokens** -- API 접근을 위한 SHA-256 해시 토큰 인증
10. **Roles & Permissions** -- 세분화된 역할 기반 접근 제어
11. **Audit Logging** -- 모든 작업에 대한 완전한 감사 추적
12. **Import System** -- 구조화된 데이터에서 티켓 대량 가져오기
13. **Side Conversations** -- 티켓 내 비공개 스레드 대화
14. **Ticket Merging & Linking** -- 중복 티켓 병합 및 관련 티켓 연결
15. **Ticket Splitting** -- 복잡한 티켓을 별도의 이슈로 분할
16. **Ticket Snooze** -- `@Scheduled`를 통한 자동 깨우기 티켓 스누즈
17. **Email Threading** -- Thymeleaf를 통한 브랜드 HTML 이메일 템플릿과 올바른 Message-ID 스레딩
18. **Saved Views** -- 에이전트별 사용자 정의 필터/정렬 티켓 뷰
19. **Widget API** -- 지원 위젯 임베딩을 위한 공개 REST 엔드포인트
20. **Real-time Broadcasting** -- STOMP/SockJS를 통한 WebSocket (옵트인)
21. **Capacity Management** -- 에이전트 워크로드 제한 추적 및 적용
22. **Skill-based Routing** -- 매칭되는 스킬을 가진 에이전트에게 티켓 라우팅
23. **CSAT Ratings** -- 토큰 기반 접근을 통한 고객 만족도 설문조사
24. **2FA (TOTP)** -- 에이전트 계정을 위한 시간 기반 일회용 비밀번호 지원
25. **Guest Access** -- 인증 없는 토큰 기반 티켓 접근

## 요구 사항

- Java 17+
- Spring Boot 3.2+
- 관계형 데이터베이스 (PostgreSQL, MySQL 또는 개발용 H2)

## 설치

`build.gradle.kts`에 종속성을 추가합니다:

```kotlin
implementation("dev.escalated:escalated-spring:0.1.0")
```

또는 `pom.xml`:

```xml
<dependency>
    <groupId>dev.escalated</groupId>
    <artifactId>escalated-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

## 구성

`application.properties` 또는 `application.yml`에 추가합니다:

```properties
# Enable/disable the helpdesk
escalated.enabled=true

# Route prefix (default: escalated)
escalated.route-prefix=escalated

# Feature toggles
escalated.knowledge-base.enabled=true
escalated.broadcasting.enabled=false
escalated.two-factor.enabled=true
escalated.widget.enabled=true
escalated.guest-access.enabled=true

# SLA checking interval
escalated.sla.check-interval-seconds=60

# Snooze wake-up interval
escalated.snooze.check-interval-seconds=60

# Webhook settings
escalated.webhook.max-retries=3

# Database (example for PostgreSQL)
spring.datasource.url=jdbc:postgresql://localhost:5432/myapp
spring.datasource.username=user
spring.datasource.password=secret
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

## 데이터베이스 설정

Flyway 마이그레이션이 포함되어 자동으로 실행됩니다. 마이그레이션은 `escalated_` 접두사가 붙은 모든 테이블을 생성하고 기본 역할과 권한을 시드합니다.

## API 엔드포인트

### Admin (`/escalated/api/admin/`)
| 메서드 | 경로 | 설명 |
|--------|------|-------------|
| GET | `/tickets` | 티켓 목록 (페이지네이션, 필터 가능) |
| POST | `/tickets` | 티켓 생성 |
| GET | `/tickets/{id}` | 티켓 조회 |
| PUT | `/tickets/{id}` | 티켓 수정 |
| POST | `/tickets/{id}/assign` | 티켓 할당 |
| POST | `/tickets/{id}/status` | 상태 변경 |
| POST | `/tickets/{id}/snooze` | 티켓 스누즈 |
| POST | `/tickets/{id}/merge` | 티켓 병합 |
| POST | `/tickets/{id}/split` | 티켓 분할 |
| DELETE | `/tickets/{id}` | 티켓 삭제 |
| GET/POST | `/departments` | 부서 관리 |
| GET/POST | `/agents` | 에이전트 관리 |
| GET/POST | `/webhooks` | Webhook 관리 |
| GET/POST | `/roles` | 역할 관리 |
| GET/POST | `/custom-fields` | 사용자 정의 필드 관리 |
| GET/POST | `/settings` | 설정 관리 |
| GET | `/audit-logs` | 감사 로그 보기 |
| POST | `/import/tickets` | 티켓 가져오기 |
| GET/POST | `/kb/categories` | KB 카테고리 관리 |
| GET/POST | `/kb/articles` | KB 기사 관리 |

### Agent (`/escalated/api/agent/`)
| 메서드 | 경로 | 설명 |
|--------|------|-------------|
| GET | `/tickets` | 할당/필터된 티켓 목록 |
| GET | `/tickets/{id}` | 티켓 보기 |
| POST | `/tickets/{id}/replies` | 답변 추가 |
| POST | `/tickets/{id}/macro/{macroId}` | 매크로 적용 |
| POST | `/tickets/{id}/side-conversations` | 사이드 대화 생성 |
| POST | `/tickets/{id}/links` | 티켓 연결 |
| GET/POST | `/saved-views` | 저장된 뷰 관리 |
| GET/POST | `/canned-responses` | 정형 응답 관리 |

### Customer (`/escalated/api/customer/`)
| 메서드 | 경로 | 설명 |
|--------|------|-------------|
| GET | `/tickets?email=` | 고객 티켓 목록 |
| POST | `/tickets` | 티켓 생성 |
| POST | `/tickets/{id}/replies` | 답변 추가 |

### Widget (`/escalated/api/widget/`)
| 메서드 | 경로 | 설명 |
|--------|------|-------------|
| POST | `/tickets` | 티켓 생성 (공개) |
| GET | `/tickets/{token}` | 게스트 토큰으로 티켓 보기 |
| POST | `/tickets/{token}/replies` | 게스트 토큰으로 답변 |
| GET | `/kb/search?query=` | 지식 베이스 검색 |
| POST | `/csat/{token}` | 만족도 평가 제출 |

### Guest (`/escalated/api/guest/`)
| 메서드 | 경로 | 설명 |
|--------|------|-------------|
| GET | `/tickets/{token}` | 티켓 보기 |
| GET | `/tickets/{token}/replies` | 답변 보기 |
| POST | `/tickets/{token}/replies` | 답변 추가 |

## 아키텍처

```
dev.escalated/
  config/              자동 구성, 속성, WebSocket 구성
  models/              완전한 관계를 가진 JPA 엔티티
  repositories/        Spring Data JPA 리포지토리
  services/            비즈니스 로직 (트랜잭션)
  controllers/
    admin/             관리자 REST API
    agent/             에이전트 REST API
    customer/          고객 REST API
    widget/            공개 위젯 API
  events/              Spring 애플리케이션 이벤트 + Webhook 리스너
  security/            API 토큰 인증 필터, 보안 구성, 2FA
  scheduling/          @Scheduled 작업 (스누즈, SLA, 자동화)
```

## 인증

API 엔드포인트는 Bearer 토큰 인증을 사용합니다. 관리자 API를 통해 토큰을 생성합니다:

```bash
curl -X POST /escalated/api/admin/tokens \
  -H "Content-Type: application/json" \
  -d '{"name": "My API Token", "agent_id": 1}'
```

응답에는 평문 토큰이 포함됩니다 (한 번만 표시). 후속 요청에서 사용합니다:

```bash
curl -H "Authorization: Bearer <token>" /escalated/api/agent/tickets
```

## WebSocket (실시간)

`escalated.broadcasting.enabled=true`로 활성화합니다. SockJS/STOMP를 통해 `/escalated/ws`에 연결합니다.

## 개발

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```

## 라이선스

MIT 라이선스. 자세한 내용은 [LICENSE](LICENSE)를 참조하세요.
