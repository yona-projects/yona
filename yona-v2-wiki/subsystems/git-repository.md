---
title: Git 저장소 서비스 (JGit)
type: subsystem
sources: [app/playRepository/GitRepository.java:86-96, app/playRepository/GitRepository.java:155-192, app/playRepository/GitRepository.java:640-660, app/playRepository/GitRepository.java:955-972, app/playRepository/RepositoryService.java:62-74, app/utils/Config.java:274-280, app/utils/FileUtil.java:36-49]
updated: 2026-10-22
---

# Git 저장소 서비스

v1.6 `playRepository.GitRepository`와 `RepositoryService`의 저장소 위치, 생성, 삭제, 열기를 옮겼다. 방식은 선생님이 확정하셨다: JGit 7.x를 프로세스 안에서 사용한다([../decisions.md](../decisions.md) D-15). 분석은 `~/yona-convert/yona-v1.6-wiki/subsystems/git-svn-repository.md`를 본다.

## 구성

| 클래스 (`dev.yona.repository`) | 역할 | v1.6 |
|---|---|---|
| `DataDirectory` | 데이터 디렉터리의 루트 결정 | `Config.getYonaDataDir` |
| `GitRepositories` | 저장소 위치, bare 저장소 생성, 삭제, 열기 (JGit) | `GitRepository` |
| `RepositoryService` | 프로젝트의 `vcs`로 종류를 골라 만들고 지우고 여는 진입점 | `RepositoryService` |

## 데이터 디렉터리 (D-16)

우선순위: 설정 `yona.data-dir` > JVM 시스템 속성 `-Dyona.data`(v1.6) > 작업 디렉터리. 기존 v1.6 설치의 `-Dyona.data`와 그 아래 디렉터리 구조를 그대로 쓸 수 있다. 첨부 파일도 같은 디렉터리를 쓴다(첨부 이식 때 추가).

## 저장소 위치

`<데이터 디렉터리>/repo/git/<소유자>/<이름>.git` (작업 트리가 없는 bare 저장소). v1.6과 같다. 소유자와 이름은 URL 디코딩이 끝난 값을 쓴다. 한글 이름도 그대로 디렉터리가 된다.

v1.6의 병합용 저장소(`repo/git-merging/<소유자>/<이름>.git`)는 만들지 않는다. 병합이 받는 저장소 안에서 일어나기 때문이다([../architecture/differences-from-v16.md](../architecture/differences-from-v16.md)). Subversion 저장소(`repo/svn/<소유자>/<이름>`)는 [web/subversion.md](../web/subversion.md)에 있다.

## 동작

- `createRepository(project)`: 같은 위치의 저장소를 먼저 지우고(v1.6 `RepositoryService.createRepository`) bare 저장소를 만든다.
- `deleteRepository(project)`: JGit의 창 캐시(`WindowCacheConfig`)를 다시 설치해 pack 파일 참조를 비운 뒤 디렉터리를 재귀 삭제한다. 없으면 아무 일도 하지 않는다. v1.6의 `repository.close()` 호출은 저장소를 호출한 쪽이 `use { }`로 닫는 방식으로 바꿨다.
- `openRepository(project)`: JGit `Repository`를 연다. 호출한 쪽이 닫아야 한다.
- 알 수 없는 VCS는 `UnsupportedOperationException`이다. Subversion은 `SvnRepositories`(SVNKit)가 맡는다(D-13). 열기, 복제, 가져오기는 Git만이다.

## v1.6과 달라진 점

| 항목 | v1.6 | v2 | 이유 |
|---|---|---|---|
| JGit 버전 | 4.5.0 | 7.8.0 | Java 21 지원과 보안 패치 (D-15) |
| 삭제 | `FileUtil.rm_rf`(실패를 무시) | `deleteRecursively` | 같은 결과를 표준 라이브러리로 |
| 저장소 닫기 | `delete()`가 자기 `repository`를 닫음 | 열린 `Repository`는 호출한 쪽이 `use`로 닫음 | 상태를 가진 객체를 없앴다 |

## 이후 구현된 부분

smart-HTTP 서빙, push 훅 체인, 포크 복제, 파일과 커밋 조회, 아카이브는 모두 구현했다([../web/code-browser.md](../web/code-browser.md), [git-http.md](git-http.md) 등 5단계 문서).

## 검증

`GitRepositoryServiceTest` 9건(위치, bare 저장소 생성, 재생성 시 이전 내용 삭제, 삭제, 없는 저장소 삭제, 연 상태에서 삭제, 한글 이름, 알 수 없는 VCS, Subversion 미지원), `DataDirectoryTest` 4건.
