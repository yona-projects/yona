---
title: 포크
type: web
sources: [app/controllers/PullRequestApp.java:72-153, app/views/git/fork.scala.html, app/views/git/clone.scala.html, app/models/Project.java:604-616, app/models/Project.java:786-816, app/playRepository/GitRepository.java:170-180, app/playRepository/GitRepository.java:1739-1775]
updated: 2026-10-23
---

# 포크

## 주소

| 주소 | 설명 |
|---|---|
| `GET /:user/:project/newFork?forkOwner=` | 포크 폼. 로그인 필요, 포크 만들기 권한 필요 |
| `POST /:user/:project/fork` | 같은 이름이 있으면 `project.name.duplicate` 경고와 함께 폼으로 303, 없으면 복제 안내 화면 |
| `POST /:user/:project/clone` | 복제 실행. `{"status":"success"|"failed","url":...}` |

## 동작

- 포크가 놓일 소유자는 `forkOwner`가 사용자가 관리하는 조직이면 그 조직, 아니면 사용자 자신이다. 폼의 소유자 선택지는 사용자 자신과 관리하는 조직이다.
- 이름 제안은 소유자에게 같은 이름이 있으면 `이름-1`, `이름-2`로 번호를 붙인다. 이미 포크한 프로젝트가 있으면 안내하고 링크를 건다.
- 복제 안내 화면은 3초 뒤 `POST /clone`을 부르고 결과의 주소로 이동한다. 실패하면 `fork.failed` 대화상자를 먼저 보여 준다.
- 복제는 객체 파일을 하드 링크로 걸고 참조(HEAD 포함)를 옮긴다. 하드 링크를 만들 수 없으면 만들던 저장소를 지우고 전체를 복사해 복제한다. `info/alternates`는 v1.6의 비교 결함 때문에 복사하지 않고 하드 링크로 건다(결과 동일).
- 새 프로젝트는 원본의 설명, VCS, 메뉴 설정을 물려받고 `originalProject`를 원본으로 한다. 소유자가 조직이면 조직에 연결한다. 만든 사람은 MANAGER, 사이트 관리자(1번)는 SITEMANAGER 역할이다. 구독과 방문 기록은 남기지 않는다.
- 이름이 규칙에 맞지 않거나 공개 범위가 잘못되면 v1.6처럼 500이다(입력 묶기 오류를 `get()`으로 읽다 예외). 이 덕분에 경로를 벗어나는 이름은 저장소가 만들어지기 전에 막힌다.
- 복제가 실패하면 프로젝트는 남기지 않고 만들던 저장소 디렉터리를 지운다.

차이는 A-73을 본다.
