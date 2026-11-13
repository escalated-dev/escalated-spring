<p align="center"><sub>
  <a href="README.ar.md">العربية</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="../../README.md">English</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.fr.md">Français</a> ·
  <a href="README.it.md">Italiano</a> ·
  <a href="README.ja.md">日本語</a> ·
  <b>한국어</b> ·
  <a href="README.nl.md">Nederlands</a> ·
  <a href="README.pl.md">Polski</a> ·
  <a href="README.pt-BR.md">Português (BR)</a> ·
  <a href="README.ru.md">Русский</a> ·
  <a href="README.tr.md">Türkçe</a> ·
  <a href="README.zh-CN.md">简体中文</a>
</sub></p>

<p align="center">
  <a href="https://escalated.dev"><img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/logo.png" width="72" height="72" alt="Escalated"></a>
</p>

<h1 align="center">Escalated for Spring</h1>

<p align="center">
  Spring Boot 앱에 완전한 헬프데스크를 추가하세요.<br>
  티켓, SLA, 에스컬레이션 규칙, 지식 베이스, 지원 위젯을 여러분의 데이터베이스에서 동작하는 하나의 의존성으로 제공합니다.
</p>

<p align="center">
  <a href="https://escalated.dev"><b>웹사이트</b></a> ·
  <a href="https://escalated.dev/docs"><b>문서</b></a> ·
  <a href="https://escalated.dev/cloud"><b>Cloud</b></a> ·
  <a href="../../CHANGELOG.md"><b>변경 내역</b></a>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-dark.png">
  <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/inbox-light.png" alt="Escalated 상담원 받은편지함: 개수가 표시된 저장된 보기, 모든 티켓의 SLA 카운트다운과 우선순위" width="100%">
</picture>

## 주요 기능

<table>
  <tr>
    <td width="40%">
      <h3>상담원이 하루 종일 일할 수 있는 받은편지함</h3>
      대화, 내부 메모, 상용구 답변, SLA 타이머를 한 화면에서 볼 수 있습니다. 티켓은 Postmark, Mailgun 또는 AWS SES를 통해 이메일로도 들어옵니다.
    </td>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/ticket-light.png" alt="답장 작성기가 표시된 상담원 작업 공간에서 열린 티켓">
      </picture>
    </td>
  </tr>
  <tr>
    <td width="60%">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-dark.png">
        <img src="https://raw.githubusercontent.com/escalated-dev/escalated/main/docs/assets/readme/dashboard-light.png" alt="응답 시간, CSAT, SLA 위반을 보여주는 관리자 보고서 페이지">
      </picture>
    </td>
    <td width="40%">
      <h3>한눈에 보이는 SLA와 에스컬레이션 규칙</h3>
      영업시간과 휴일을 반영한 응답 및 해결 목표, 위반 시 에스컬레이션하거나 재할당하는 규칙, SLA 보고서를 제공합니다.
    </td>
  </tr>
</table>

## 기능

<table>
<tr>
<td width="33%" valign="top">

**티켓과 받은편지함**
- 상태, 우선순위, 담당자 지정
- 매크로와 상용구 답변
- 티켓 내 사이드 대화
- 티켓 병합, 연결, 분할
- 자동으로 다시 열리는 스누즈
- 상담원별 저장된 보기
- 여러 유형의 사용자 정의 필드

</td>
<td width="33%" valign="top">

**SLA와 라우팅**
- 영업시간과 휴일을 반영한 SLA 정책
- 위반, 경과 시간, 무응답 시 에스컬레이션
- 재할당, 우선순위 변경, 부서 이동
- 자동 종료 및 자동 할당 규칙
- 스킬 기반 라우팅
- 상담원 업무량 한도

</td>
<td width="33%" valign="top">

**이메일**
- Postmark, Mailgun, AWS SES를 통한 수신
- 서명된 Reply-To로 티켓 식별
- 요청자의 답장만 수락
- Thymeleaf로 만드는 브랜드 HTML 이메일
- Message-ID 기반 스레딩

</td>
</tr>
<tr>
<td width="33%" valign="top">

**고객**
- 공개 위젯 API
- 토큰을 통한 게스트 티켓 접근
- 토큰으로 접근하는 CSAT 설문
- 검색과 피드백을 지원하는 지식 베이스

</td>
<td width="33%" valign="top">

**관리와 보안**
- 역할과 권한
- 직원용 TOTP 2단계 인증
- SAML 2, OAuth 2 / OIDC, JWT 로그인
- 모든 작업의 감사 기록
- 해시 처리된 API 토큰

</td>
<td width="33%" valign="top">

**확장과 연동**
- 재시도를 지원하는 HMAC 서명 웹훅
- 티켓 일괄 가져오기
- 네이티브 Java 플러그인과 SDK 플러그인
- STOMP/SockJS 기반 WebSocket (선택 사항)
- 공용 Vue 3 + Inertia UI (선택 사항)

</td>
</tr>
</table>

<p align="center"><sub><a href="../features.md">모든 기능 자세히 보기</a></sub></p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"></a>
  <a href="https://vuejs.org"><img src="https://img.shields.io/badge/Vue.js-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white" alt="Vue.js"></a>
  <a href="https://inertiajs.com"><img src="https://img.shields.io/badge/Inertia.js-9553E9?style=for-the-badge&logo=inertia&logoColor=white" alt="Inertia.js"></a>
  <a href="https://www.postgresql.org"><img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL"></a>
</p>

## 기능 목록

```bash
git clone https://github.com/escalated-dev/escalated-spring.git
cd escalated-spring
./gradlew publishToMavenLocal
```

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("dev.escalated:escalated-spring:0.2.13")
}
```

그런 다음 `mavenLocal()`와 함께 `implementation("dev.escalated:escalated-spring:0.2.13")`를 앱에 추가하고 `escalated.enabled=true`를 설정하세요. Java 17 이상과 Spring Boot 4.1이 필요합니다. 설정, 데이터베이스, 로그인은 [시작 가이드](../getting-started.md)에서 다루며, [Inertia 페이지](../ui.md)는 `escalated.ui.enabled=true`로 공용 화면을 켭니다.

## 그, 첨부 파일, 활동 타임라인

- [전체 기능](../features.md) · [시작하기](../getting-started.md) · [Inertia 페이지](../ui.md)
- [사용자 정의, 현지화, 실시간](../customization.md) · [아키텍처](../architecture.md) · [API 엔드포인트](../api-endpoints.md)
- [접근 제어](../access-control.md) · [티켓 접근과 라우팅](../ticket-access-and-routing.md) · [사용자 정의 필드](../custom-fields.md)
- [수신 이메일](../inbound-email.md) · [게스트 접근](../guest-access.md) · [위젯 삽입](../embedding-the-widget.md)
- [관리자 화면 삽입](../embedding-admin.md) · [상담원 패널](../agent-panel.md) · [플러그인](../plugins.md)
- [뉴스레터](../newsletters.md) · [개발](../development.md)

Escalated는 [Laravel](https://github.com/escalated-dev/escalated-laravel), [Rails](https://github.com/escalated-dev/escalated-rails), [Django](https://github.com/escalated-dev/escalated-django), [NestJS](https://github.com/escalated-dev/escalated-nestjs), [AdonisJS](https://github.com/escalated-dev/escalated-adonis), [Symfony](https://github.com/escalated-dev/escalated-symfony), [Phoenix](https://github.com/escalated-dev/escalated-phoenix), [Go](https://github.com/escalated-dev/escalated-go), [.NET](https://github.com/escalated-dev/escalated-dotnet), [WordPress](https://github.com/escalated-dev/escalated-wordpress), [Filament](https://github.com/escalated-dev/escalated-filament)용으로도 제공됩니다. UI는 [escalated](https://github.com/escalated-dev/escalated) 저장소에 있습니다.

## 

MIT. Copyright (c) 2026 Escalated.dev. [LICENSE](../../LICENSE)를 참고하세요.
