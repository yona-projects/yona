# Yona v2 Wiki 인덱스

규약은 [CLAUDE.md](CLAUDE.md), 변경 이력은 [log.md](log.md)를 본다. v1.6 분석은 `~/yona-convert/yona-v1.6-wiki`를 참조한다.

## 아키텍처 (`architecture/`)

- [설정 키 대응표](architecture/configuration-keys.md): `application.*` -> `yona.*`
- [v1.6과 달라지는 아키텍처 결정](architecture/differences-from-v16.md): 기술 스택 전환과 그 이유
- [기술 스택 결정 기록](architecture/technology-decisions.md): 쿼리 계층, 스키마 관리, 검증 DB, 테스트 스택과 테스트 DB 운영 방식

## 웹 (`web/`)

- [정적 자원, LESS 빌드, 다국어 메시지](web/static-assets-and-i18n.md): `/assets/`, favicon, Less 1.7.5 빌드, Play 메시지 형식
- [홈 화면](web/home-page.md): 소개, 환영 표, 내 활동 탭, 기본 로그인 페이지, 알림 조회
- [레이아웃과 로그인 화면](web/layout-and-login-page.md): Scala -> Thymeleaf 변환 규칙, 공통 모델, 로그인 화면, `/messages.js`

## 도메인 (`domain/`)

- [v1.6 스키마와 JPA 엔티티 매핑](domain/schema-mapping.md): 기준 스키마 추출, 3개 DB 검증 테스트, 패키지 구성, 매핑 규칙, v1.6과 달라진 점

## 서브시스템 (`subsystems/`)

- [초기 데이터 적재](subsystems/initial-data.md): 관리자 계정, 사이트 관리자, 역할 7종
- [접근 제어](subsystems/access-control.md): `@AnonymousCheck`, 앞으로 옮길 `AccessControl`
- [인증](subsystems/authentication.md): v1.6 호환 패스워드 해시와 로컬 로그인 판정 규칙

## 기타

- [v1.6 결함 재현 목록](refactoring-backlog.md): v1.6의 결함을 그대로 재현한 부분과 개선 후보
