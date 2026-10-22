---
title: v1.6과 달라지는 아키텍처 결정
type: architecture
sources: [build.sbt, conf/application.conf]
updated: 2026-10-22
---

# v1.6과 달라지는 아키텍처 결정

기능과 계약은 v1.6과 같게 유지하고, 구현 기반만 아래처럼 바꾼다.

| 항목 | v1.6 | v2 | 이유 |
|---|---|---|---|
| 언어 | Java, Scala 템플릿 | Kotlin (패키지 `dev.yona`) | 프로젝트 요구사항 |
| 프레임워크 | Play 2.3.10 | Spring Boot 4.1.1 (Spring Framework 7) | 프로젝트 요구사항 |
| ORM | Ebean | JPA(Hibernate) | Spring 표준 영속성 계층 |
| 빌드 | sbt | Gradle Kotlin DSL (JDK 21 toolchain) | 프로젝트 요구사항 |
| 템플릿 | `*.scala.html` | Thymeleaf | Scala 템플릿 엔진 대체 |
| DB | MariaDB | H2(기본), PostgreSQL(컨테이너), 이후 MariaDB 등 | 다중 백엔드 요구사항 |
| 개발 인프라 | 없음 | `docker-compose.yml` (PostgreSQL 16, mailpit) | 메일 확인과 DB 구성 |

테이블 구조는 v1.6의 `conf/evolutions/default/*.sql`과 같게 유지한다.
