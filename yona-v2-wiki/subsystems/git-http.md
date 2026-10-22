---
title: Git smart-HTTP 서비스
type: subsystem
sources: [app/controllers/GitApp.java, app/playRepository/RepositoryService.java:183-300, app/utils/BasicAuthAction.java, app/playRepository/hooks/*]
updated: 2026-10-23
---

# Git smart-HTTP 서비스

`git clone`, `fetch`, `push`가 쓰는 주소이다. 구현: `GitHttpController`(웹), `GitHttpService`(JGit `UploadPack`, `ReceivePack`), `GitHooks`(푸시 전후 훅).

| 요청 | 동작 |
|---|---|
| `GET /:owner/:project/info/refs?service=git-upload-pack\|git-receive-pack` | `# service=…` 한 줄과 ref 광고. `service`가 없으면 `403`("getanyfile") |
| `POST /:owner/:project/git-upload-pack` | 복제와 가져오기 |
| `POST /:owner/:project/git-receive-pack` | 푸시 |

## 인증과 권한

- 인증은 HTTP Basic(`loginId:password`, ISO-8859-1)이다. Basic 헤더가 아니면 세션, 토큰, 쿠키로 식별한 사용자이다. 자격이 틀리면 익명으로 본다. 콜론이 없는 헤더는 `400`.
- 복제는 읽기, 푸시는 수정 권한(`CODE` 리소스)이다. 코드를 구성원에게만 여는 프로젝트(`is_code_accessible_member_only`)는 구성원이 아니면 읽기도 수정 권한을 요구한다.
- 권한이 없을 때 익명이면 `401`과 `WWW-Authenticate: Basic realm="Yobi"`, 로그인한 사용자이면 `403`과 `git.error.permission` 문구이다. 클라이언트가 Git 2.1.0 미만이거나 `User-Agent` 버전이 올바른 세 자리 버전이 아니면 문자 집합 없이 `git.error.notAllowedCharset` 문구를 보낸다(JGit 클라이언트도 여기 해당한다).
- 프로젝트가 없으면 `404`, 이름이 바뀌었으면 이전 이름으로도 찾는다. Git이 아닌 프로젝트는 `404`. 지원하지 않는 서비스는 `403`.
- 푸시나 복제 요청을 처리한 로그인 사용자는 최근 방문한 프로젝트로 기록한다.

## 푸시 훅

- 푸시 전: `refs/yobi` 아래 ref는 거부한다(내부용 예약).
- 푸시 뒤: 프로젝트의 마지막 푸시 시각(`last_pushed_date`)을 갱신하고, 최근 푸시한 브랜치(`pushed_branch`)를 기록한다. 한 시간이 지난 기록은 지우고, 태그와 이미 열린/거절된 풀 리퀘스트가 있는 브랜치는 새로 기록하지 않으며, 지운 브랜치의 기록은 지운다.
- 이슈 언급 이벤트(`IssueReferredFromCommitEvent`), 풀 리퀘스트 확인(`PullRequestCheck`), 새 커밋 알림(`NotifyPushedCommits`)은 해당 기능을 옮길 때 이 목록에 더한다(로드맵 5단계 하위 단위).

## v1.6과 달라진 점

[decisions.md] A-66 참고. 요약: 서블릿 스레드에서 응답에 바로 쓴다(v1.6은 스레드와 파이프), gzip으로 압축된 요청 본문을 풀어서 읽는다, 푸시 뒤 훅 하나가 실패해도 다음 훅을 계속 돌린다.

## 테스트

`GitHttpTest` 10건. 서버를 임의 포트로 띄우고 실제 JGit 클라이언트로 푸시와 복제를 한다(구성원 푸시와 익명 복제, 마지막 푸시 시각과 최근 푸시 브랜치, 익명과 잘못된 비밀번호 거부, 비구성원 거부, 예약 ref 거부, 비공개와 구성원 전용 프로젝트, 지원하지 않는 서비스, 없는 프로젝트, 광고 콘텐츠 유형).
