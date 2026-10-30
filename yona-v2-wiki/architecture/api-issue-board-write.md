---
title: 이슈와 게시판 쓰기 REST(X-29) 추적표
type: architecture
sources:
  - app/controllers/api/IssueApi.java (60a5ac406)
  - app/controllers/api/BoardApi.java (60a5ac406)
  - conf/routes (60a5ac406)
  - https://docs.github.com/en/rest/issues
updated: 2026-10-30
---

# 이슈와 게시판 쓰기 REST (X-29) 추적표

결정은 [D-47](../decisions.md)이다. 이 표는 **빠지는 것이 없게** 엔드포인트마다 상태를 적는 추적표이고, 구현할 때마다 상태를 고친다. 경로 규칙은 D-35 14번, D-39 1번과 같다: **v1.6의 `/-_-api/v1/owners/{owner}/projects/{projectName}` 접두사를 쓰고 그 아래 자원 경로와 메서드는 GitHub 방식**이다. 모두 토큰 전용이며(X-5, [api-auth-split.md](api-auth-split.md)) 범위는 `repo`, `public_repo`(조직과 사용자 범위는 해당 항목에 적는다). v1.6 외부 API의 계약은 바꾸지 않고 필드와 경로만 더한다.

상태: **완료**(v1.6에서 이미 이식), **대기**(이번 X-29에서 구현), **단계**(구현할 단계 a~d).

## 이미 있는 것(v1.6 이식 완료, X-5 이후)

| 메서드와 경로(`.../projects/{projectName}` 뒤) | 설명 | 상태 |
|---|---|---|
| `GET issues/{number}` | 이슈 한 건 | 완료 |
| `POST issues` | 이슈 만들기 | 완료 |
| `PUT issues/{number}` | 제목, 본문, 마일스톤 제목, 상태, 담당자(이메일)를 한꺼번에 바꿈 | 완료 |
| `PATCH issues/{number}` | 상태만 바꿈(`state`) | 완료, a단계가 부분 수정으로 확장함(2026-10-30) |
| `POST issues/{number}/comments` | 댓글 달기(토큰 흐름과 가져오기 흐름) | 완료 |
| `POST issuelabel/{number}` | 라벨 붙이기와 떼기 | 완료 |
| `POST issues/imports` | 이슈 가져오기 | 완료 |
| `POST posts`, `POST posts/{number}/comments`, `GET posts/{number}/watchers` | 게시글 만들기, 댓글, 지켜보는 사람 | 완료 |
| `POST labels`, `POST milestones` | 라벨과 마일스톤 만들기 | 완료 |
| `GET issues`, `GET labels`, `GET milestones`, `GET members`, `GET assignableusers` | 조회(X-19 1차) | 완료 |

## 권한 보정(X-29a, 2026-10-30)

D-36 4번("모든 토큰이 범위와 사용자의 실제 권한의 교집합만 할 수 있다")은 문서에는 있었으나 외부 이슈 API의 조회, `PUT`, `PATCH`, 토큰 댓글에는 적용되지 않은 채였다(옛 시험이 v1.6 동작인 "구성원이 아니어도 읽고 고칠 수 있다"를 확인하고 있었다). a단계에서 이 경로들에 사용자의 실제 권한 확인을 더하고 그 옛 시험 두 개를 고쳤다. 읽기는 `READ`, 수정은 `UPDATE`, 삭제는 `DELETE`, 댓글은 댓글 만들기 권한을 따른다. **게시판과 프로젝트 쪽 외부 API(`POST posts`, `POST labels` 등)에 같은 누락이 있는지는 b단계와 c단계에서 하나씩 확인한다.**

Yona의 권한 모델에서는 프로젝트 구성원이면 이슈와 댓글을 고치고 지울 수 있다(작성자 제한이 없다). 구성원이 아닌 토큰은 거절된다.

## 이번에 더하는 것

### a단계: 이슈 수정과 이슈에 붙은 것 (X-29a)

| 메서드와 경로 | 설명 | 상태 |
|---|---|---|
| `PATCH issues/{number}` | 부분 수정: `title`, `body`, `state`, `milestone`(번호 또는 `null`), `assignees`(로그인 ID 목록), `labels`(번호 목록). 보낸 항목만 바꾼다. 상태만 보내는 v1.6 호출은 그대로 동작한다 | **완료**(2026-10-30) |
| `DELETE issues/{number}` | 이슈 삭제(삭제 권한) | **완료**(2026-10-30) |
| `GET issues/{number}/comments` | 이슈 댓글 목록 | **완료**(2026-10-30) |
| `PATCH issues/{number}/comments/{commentId}` | 댓글 수정(작성자나 관리자) | **완료**(2026-10-30) |
| `DELETE issues/{number}/comments/{commentId}` | 댓글 삭제(작성자나 관리자) | **완료**(2026-10-30) |
| `POST issues/{number}/labels`, `DELETE issues/{number}/labels/{labelId}` | 라벨 하나를 붙이거나 뗌 | **완료**(2026-10-30) |
| `POST issues/{number}/assignees`, `DELETE issues/{number}/assignees` | 담당자를 더하거나 뺌(`{"assignees": [로그인 ID]}`) | **완료**(2026-10-30) |

### b단계: 게시판 (X-29b)

| 메서드와 경로 | 설명 | 상태 |
|---|---|---|
| `GET posts`, `GET posts/{number}` | 게시글 목록과 한 건 | 대기 |
| `PATCH posts/{number}` | 제목, 본문 부분 수정 | 대기 |
| `DELETE posts/{number}` | 게시글 삭제 | 대기 |
| `GET posts/{number}/comments`, `PATCH posts/{number}/comments/{commentId}`, `DELETE posts/{number}/comments/{commentId}` | 댓글 목록, 수정, 삭제 | 대기 |
| `POST posts/{number}/labels`, `DELETE posts/{number}/labels/{labelId}` | 게시글 라벨 | 대기 |

### c단계: 라벨과 마일스톤 관리 (X-29c)

| 메서드와 경로 | 설명 | 상태 |
|---|---|---|
| `PATCH labels/{labelId}`, `DELETE labels/{labelId}` | 라벨 고치기, 지우기 | 대기 |
| `PATCH milestones/{number}`, `DELETE milestones/{number}` | 마일스톤 고치기(제목, 설명, 마감일, 상태), 지우기 | 대기 |

### d단계: 이슈의 Yona 고유 쓰기 기능 (X-29d)

웹 화면에만 있던 것(`/{o}/{p}/issue/{n}/…`, 세션 전용)의 토큰 버전이다. GitHub에 없는 기능이라 Yona 방식으로 만든다.

| 메서드와 경로 | 설명 | 상태 |
|---|---|---|
| `GET issues/{number}/sharers`, `POST issues/{number}/sharers`, `DELETE issues/{number}/sharers` | 공유자 조회, 더하기, 빼기 | 대기 |
| `POST issues/{number}/weight` | 중요도(가중치) 올리기와 내리기(`{"delta": 1}`) | 대기 |
| `POST issues/{number}/watchers`, `DELETE issues/{number}/watchers` | 지켜보기와 해제 | 대기 |

## 다루지 않는 것(이유)

- `detectChange`(편집 충돌 감지)와 `commentNotiReceivers`(댓글 알림 수신자 지정)는 웹 편집기용이라 외부 REST로 만들지 않는다.
- `POST /files`(첨부 올리기)의 토큰 버전은 X-5(D-36 7-2)에서 처리한다.
- 사용자와 조직과 검색과 웹훅은 X-30, OpenAPI 문서는 X-31이다.
