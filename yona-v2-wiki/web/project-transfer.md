---
title: 프로젝트 이전과 저장소 종류 변경
type: web
sources: [app/controllers/ProjectApp.java:596-690, app/controllers/ProjectApp.java:714-790, app/views/project/transfer.scala.html, app/views/project/change_vcs.scala.html, app/models/ProjectTransfer.java, app/models/Project.java:658-670, app/models/Project.java:786-799, app/models/Project.java:891-897]
updated: 2026-10-22
---

# 프로젝트 이전

## 화면 (`GET /:user/:project/transfer`)과 요청 (`PUT /:user/:project/transfer?owner=...`)

프로젝트를 지울 수 있는 사용자(`DELETE` 권한)만 쓴다. 화면은 새 소유자 입력, 안내 5줄, 동의 체크박스, 이전 버튼(확인 대화상자), 스크립트 `project.Transfer`로 구성된다.

| 상황 | 응답 |
|---|---|
| `owner`가 사용자도 조직도 아님(없거나 비어도) | `400` |
| 이미 그 사용자의 프로젝트이거나 그 조직의 프로젝트 | `200`으로 이전 화면을 다시 보여 주고 안내 `project.transfer.has.same.owner`가 같은 응답에서 보임 |
| 그 밖 | 이전 요청 저장, 이벤트 알림, flash `project.transfer.is.requested`, `303` 프로젝트 홈. `X-Requested-With: XMLHttpRequest`이면 `204`와 `Location` |

- 요청은 (프로젝트, 보낸 사람, 받는 곳)당 하나이다. 같은 요청을 다시 보내면 새로 만들지 않고 50자 영숫자 열쇠와 시각만 새로 정한다.
- 받는 곳이 조직이면 조직 이름이, 사용자이면 로그인 ID가 받는 곳이 된다. 받는 쪽 소유자에 같은 이름의 프로젝트가 있으면 새 이름에 `-1`, `-2`처럼 번호를 붙인다.
- v1.6은 이 때 받는 사람(사용자 또는 조직 관리자들)에게 수락 링크를 메일로 보낸다. 메일 서브시스템이 없으므로 `ProjectTransferRequestedEvent`를 발행하기만 하고, 메일 단계에서 수신기를 붙인다(A-25와 같은 방식). 수락 주소는 `/project/transfer/{id}/{열쇠}`이다.

## 수락 (`GET /project/transfer/:id/:key`, 로그인 필요)

| 상황 | 응답 |
|---|---|
| 요청이 없거나, 이미 수락했거나, 요청 후 하루가 지남 | `404` |
| 열쇠가 다름 | `400` |
| 받는 쪽이 아님(받는 사용자 본인, 또는 받는 조직의 관리자만 수락 가능) | `403` |
| 성공 | `303` 새 주소의 프로젝트 홈 |

성공하면 순서대로 다음을 한다.
1. 받는 곳에서 겹치지 않는 새 이름을 정한다.
2. 같은 (프로젝트, 보낸 사람, 받는 곳)의 요청 행을 지운다. **처리한 요청 자신도 여기서 지워진다**(v1.6 `deleteExisting`).
3. 저장소 디렉터리를 옮긴다. 실패하면 오류이고 DB 변경은 취소된다.
4. 이전 위치(소유자, 이름, 시각)를 기록한다(마지막 변경이 없거나 25시간 이상 지났을 때만).
5. 소유자, 이름을 바꾸고 조직은 받는 곳이 조직이면 그 조직, 사용자이면 없음으로 한다.
6. 보낸 사람이 관리자였으면 멤버로 낮추고, 받는 곳이 사용자이면 그 사용자를 관리자로 한다(조직이면 아무도 관리자가 되지 않는다).

# 저장소 종류 변경

## 화면 (`GET /:user/:project/changeVCS`)과 변경 (`POST /:user/:project/changeVCS`)

프로젝트를 고칠 수 있는 사용자(관리자)만 쓴다. 화면은 `현재 종류 -> 다음 종류`(Git이면 Subversion, 아니면 Git)와 경고, 동의 체크박스, 변경 버튼(확인 대화상자)이다.

변경은 README 게시글 표시를 끄고(저장소에 README 파일이 있을 때) -> 포크의 원본 연결을 끊고 -> 기존 저장소를 지우고 -> 종류를 바꿔 새 저장소를 만든다. 성공하면 `204`와 프로젝트 홈 `Location`, 실패하면 오류를 기록하고 본문 없는 `500`이다. 실패하면 DB 변경은 취소되지만 이미 지운 저장소 디렉터리는 돌아오지 않는다(v1.6도 같다).

**제약**: Subversion 저장소는 로드맵 8단계에서 지원하므로 지금은 Git에서 Subversion으로 바꾸는 요청이 `500`으로 실패한다(프로젝트 생성에서 Subversion을 고르는 경우와 같다). Subversion에서 Git으로 바꾸는 것은 된다. Subversion 프로젝트의 저장소 삭제는 지울 것이 없으므로 아무 일도 하지 않는다.

## 검증

`ProjectTransferTest` 20건, `ProjectChangeVcsTest` 7건.
