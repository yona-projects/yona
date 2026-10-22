---
title: 프로젝트 구성원 관리
type: web
sources: [app/controllers/ProjectApp.java:358-366, app/controllers/ProjectApp.java:937-1052, app/views/project/members.scala.html, app/models/ProjectUser.java, app/models/Project.java:642-656, app/models/Role.java:75-83]
updated: 2026-10-22
---

# 프로젝트 구성원 관리

## 목록 (`GET /:user/:project/members`)

프로젝트를 고칠 수 있는 사용자(관리자)만 볼 수 있다. 화면을 열 때마다 **이미 구성원인데 가입 신청이 남은 사용자의 신청을 정리**하고 승인 이벤트를 알린다(v1.6 `cleanEnrolledUsers`가 화면 조회 때 부작용으로 실행된다).

- 상단에 설정 하위 메뉴, 구성원 추가 폼(`loginId` 입력, 형식 `^[a-zA-Z0-9-]+([_.][a-zA-Z0-9-]+)*$`, 자동 완성 `/users`).
- 구성원은 사이트 관리자 역할을 뺀 목록을 이름 오름차순으로 보여 준다. 줄마다 아바타, 표시 이름, `@로그인ID`, 게스트 표시(`GUEST`).
- 소유자가 아닌 구성원: 역할 선택(관리자, 멤버)과 삭제 버튼. 소유자: `Project owner` 표시만 있다.
- 가입 신청이 있으면 "Sign-up request (n)" 아래에 신청자 목록과 추가 버튼(`enrollAcceptBtn`).

## 추가 (`POST /:user/:project/members`, 관리자)

| 상황 | 일반 요청 | `Accept: application/json` |
|---|---|---|
| 성공 | 멤버 역할로 등록, 가입 신청 정리, 승인 이벤트, **프로젝트 구독**, `303` 구성원 화면 | `200` `{}`(text/plain), **구독은 만들지 않음**(v1.6 동작) |
| 없는 사용자(`loginId`가 없는 경우 포함) | flash `project.member.notExist`, `303` 구성원 화면 | `400` `{"loginId":["User does not exist."]}` |
| 이미 구성원 | flash `project.member.alreadyMember`, `303` | `400` JSON |

## 역할 변경 (`POST /:user/:project/member/:id/edit`, 관리자)

요청 파라미터 `id`가 새 역할 번호이다. 성공하면 `204`. 대상이 소유자이면 `400`으로 "소유자는 관리자 권한이어야 한다" 화면을 보여 준다. v1.6은 역할 번호를 검사하지 않아 프로젝트 역할이 아닌 번호도 받는다(같게 재현, 존재하지 않는 번호는 외래 키 위반으로 500).

## 삭제와 탈퇴 (`DELETE /:user/:project/member/:id/delete`)

프로젝트를 읽을 수 있어야 하고, **본인이거나 관리자**여야 한다. 아니면 `403`. 소유자는 내보내거나 탈퇴할 수 없다(`403`, "소유자는 프로젝트를 떠날 수 없다"). 성공하면 `200` `{"location": ...}`을 돌려준다. 본인 탈퇴이면 프로젝트를 읽을 수 있을 때 프로젝트 홈, 아니면 `/`, 다른 사람을 내보냈으면 구성원 화면이다.

## 알림 연결

v1.6은 구성원 승인 때 `NotificationEvent.afterMemberRequest`로 알림 이벤트를 저장하고 메일을 보낸다. 알림 서브시스템은 4단계에서 옮기므로 지금은 `ProjectMemberRequestEvent`(프로젝트, 사용자, 상태, 처리한 사람)를 Spring 이벤트로 발행하기만 한다(D-14의 이벤트 방식). 알림 단계에서 이 이벤트를 받는 수신기를 추가한다.

## 검증

`ProjectMemberTest` 28건.
