---
title: 감사 로그 (X-9)
---

# 감사 로그

결정은 [decisions.md](../decisions.md) D-62이다. 엔터프라이즈 수준(GitHub Enterprise 감사 로그를 기준)으로 만든다. v1.6에 없는 추가 항목이다(D-28).

## 단계

A1 결정과 설계(이 문서), A2 핵심(기록기, 해시 사슬), A3 경계 이벤트(인증, 인가 거부), A4 계정과 토큰, A5 도메인 변경과 사이트 관리, A6 조회와 내보내기, A7 보존, A8 구조 시험과 마무리.

## 설계 개요

- **테이블**: `audit_log`(기록, 추가 전용)와 `audit_chain_head`(사슬의 끝, 한 행). 기록 한 건은 시각(UTC), 행위자 유형(사용자, 토큰, 시스템, 익명)과 번호와 로그인, 조치(`범주.조치`), 결과(성공, 실패, 거부), 대상 유형과 번호와 이름, 소속 조직과 프로젝트, IP, 사용자 에이전트, 요청 번호, 토큰 번호와 범위, 상세(JSON 글), 이전 해시와 해시를 담는다.
- **해시 사슬**: `hash = SHA-256(prev_hash + 정규화한 필드)`. 기록할 때 `audit_chain_head` 행을 비관적 잠금(`PESSIMISTIC_WRITE`)으로 잡아 순서를 직렬화하고 커밋까지 잠금을 쥔다. 그래서 사슬 순서는 커밋 순서이다.
- **기록기**: `AuditRecorder`가 행위자, IP, 사용자 에이전트, 요청 번호, 토큰을 요청 문맥에서 채운다. 비밀 값(토큰, 비밀번호, 웹훅 비밀값)은 상세에 넣지 않는다.
- **트랜잭션**: 상태 변경은 호출한 트랜잭션에 참여한다(fail-closed). 경계 이벤트는 별도 트랜잭션이고 실패해도 요청을 막지 않는다.

## A2 핵심 (구현)

- 코드: `domain/audit`의 `AuditLog`(엔티티), `AuditChainHead`(사슬의 끝), `AuditHash`, `AuditDetails`(비밀 값 가리기, 길이 제한), `AuditEvent`와 `AuditActor`, `AuditRecorder`, `AuditVerifier`, `AuditFileSink`. 설정은 `yona.audit-retention-days`(기본 365)와 `yona.audit-file-enabled`(기본 켜짐).
- `YonaAuthentication`에 `tokenId`를 더해 어느 토큰으로 한 일인지 남긴다.
- 번호는 DB 자동 증가가 아니라 사슬의 끝(`audit_chain_head`)을 비관적 잠금으로 잡고 `last_id + 1`로 매긴다. 잠금이 커밋까지 이어져 사슬 순서가 커밋 순서이다. 시각은 DB 정밀도 차이가 해시를 깨지 않게 에폭 밀리초(`created_at`)이다.
- 비밀 값 가리기: 키 이름이 비밀번호, secret, credential, authorization, signature, API 키, `token`(그 자체나 `...Token`)인 값을 `[redacted]`로 바꾼다. `tokenId`나 접두사는 그대로 둔다.
- 시험: `AuditRecorderTest` 20건.
