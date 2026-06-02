# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Ticket subjects: attach host-app entities (Project, Customer, asset, …) that a ticket is *about*, distinct from the requester. Host models implement `dev.escalated.contracts.TicketSubject`; a `TicketSubjectResolver` bean resolves type/id pairs for serialization. Tickets expose `subjects[]` on detail responses; admin attach/detach endpoints honor `escalated.ticket-subjects.types`. `subject_id` is stored as `VARCHAR(255)` for integer, UUID, or string host keys. Mirrors Laravel reference (`escalated-laravel#122`).
- Admin users-management endpoint (`GET /escalated/api/admin/users`, `PATCH /escalated/api/admin/users/{userId}/role`) backing the `Escalated/Admin/Users/Index` page in the shared frontend. Lets an admin grant or revoke the `is_admin` / `is_agent` flags from the panel, with self-demote protection so an admin cannot lock themselves out. Mirrors the Laravel reference (`escalated-laravel#94`).

### Changed
- Translations are now consumed from the central `dev.escalated:escalated-locale` Maven artifact via a chained `ReloadableResourceBundleMessageSource`. Host apps can layer sparse overrides under `classpath:i18n/overrides/messages_{locale}.properties`.

### Fixed
- Make `SimpMessagingTemplate` an optional dependency so the app boots without STOMP broker configuration (#19)
- Include `url` in attachment JSON serialization (#10)
- Include computed ticket fields in ticket JSON serialization (#11)
- Include chat, context panel, and activity fields in ticket serialization (#12)
- Include missing workflow and workflow log computed fields in serialization (#13)

### Internal
- Docker dev/demo environment under `docker/` with click-to-login agent picker and seeded profiles (#14, #18)
- Complete README translations across supported locales (#9)

## [0.1.0] — initial release

Spring Boot 3.2 port of `escalated` reaching feature parity with the Laravel reference: tickets, workflow engine, chat, KB, reports, SLA tracking, and Inertia-driven Vue frontend served through the shared `@escalated-dev/escalated` package.
