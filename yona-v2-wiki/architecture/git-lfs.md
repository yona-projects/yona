---
title: Git LFS 서버(X-8)
type: architecture
updated: 2026-11-02
---

# Git LFS 서버(X-8)

> **상태: 완료(2026-11-02). 단계 0~7 구현과 시험을 마쳤고, 실제 `git-lfs` 3.8.1과 실제 `ssh`로 HTTP, 내장 SSH 서버, `tus` 이어올리기를 확인했다. 시스템 OpenSSH 중계는 실제 sshd 없이 sshd를 흉내 낸 스크립트와 Go 도우미로 확인했다.** 계획과 결정 이유는 [git-lfs-plan.md](git-lfs-plan.md)와 [decisions.md](../decisions.md) D-79에 있다. v1.6에는 LFS 서버가 없어 새 경로만 더한 추가 항목이다(v1.6의 기존 경로와 동작은 바꾸지 않았다).

## 1. 구성

| 요소 | 위치 | 역할 |
|---|---|---|
| `LfsStorage` | `domain/lfs/LfsStorage.kt` | 프로젝트별 객체 저장(`<데이터 디렉터리>/repo/lfs/<소유자>/<이름>/objects/<oid 2자>/<2자>/<oid>`). 임시 파일에 받으며 SHA-256과 길이를 확인하고 원자적으로 옮김. 개명(`move`), 삭제, 포크로 가져가기(`adopt`), 사용량 |
| `LfsService` | `domain/lfs/LfsService.kt` | batch 판단(내려받기, 올리기, 이미 있음, 오류), 최대 크기와 프로젝트 용량 한도, 포크 원본의 객체 빌려 읽기(`locate`) |
| `LfsSupport` | `domain/lfs/LfsSupport.kt` | 저장소 종류가 LFS를 지원하는지 한 곳에서 정한다(지금은 `GIT`만, X-7에서 더함) |
| `LfsLockService` | `domain/lfs/LfsLock.kt` | 파일 잠금(`lfs_lock`). 감사 기록 포함 |
| `LfsTokens` | `domain/lfs/LfsTokens.kt` | SSH용 짧은 수명 서명 토큰(`yona_lfs_…`, 10분) |
| `LfsPointer` | `domain/lfs/LfsPointer.kt` | 포인터 파일 판별 |
| `LfsController`, `LfsLockController`, `LfsRequestSupport` | `web/lfs` | batch, 객체 전송, verify, 잠금 API와 공통 인증, 응답 |
| `LfsTusController` | `web/lfs` | `tus` 이어올리기(`POST`, `HEAD`, `PATCH`) |
| `LfsSshHandler` | `ssh/LfsSshHandler.kt` | SSH `git-lfs-authenticate` |
| `LfsDownloadController` | `web/lfs` | 코드 보기의 내려받기 링크(웹 세션) |
| `ProjectLfsController`, `SiteLfsController` | `web/project`, `web/site` | 프로젝트 설정의 LFS 화면(사용량, 잠금 목록, 강제 풀기), 사이트 설정 |

## 2. 경로

git-lfs 클라이언트는 저장소 주소 뒤에 `.git/info/lfs`를 붙인다(주소가 `.git`으로 끝나지 않으면). Yona 저장소 주소에는 `.git`이 없으므로 모든 LFS 경로가 `/{소유자}/{프로젝트}.git/info/lfs/…`와 `/{소유자}/{프로젝트}/info/lfs/…` 둘 다 받는다(기존 git HTTP 경로는 그대로).

| 경로 | 메서드 | 권한 |
|---|---|---|
| `…/info/lfs/objects/batch` | POST | 내려받기 읽기, 올리기 쓰기 |
| `…/info/lfs/objects/{oid}` | GET(이어받기 `Range`와 `HEAD` 포함), PUT | 읽기, 쓰기 |
| `…/info/lfs/verify` | POST | 쓰기 |
| `…/info/lfs/locks` | POST(만들기), GET(목록) | 쓰기, 읽기 |
| `…/info/lfs/locks/verify` | POST | 쓰기 |
| `…/info/lfs/locks/{id}/unlock` | POST | 쓰기(`force`는 프로젝트 관리자) |
| `/{소유자}/{프로젝트}/lfs/{oid}` | GET | 코드를 읽을 수 있는 웹 세션(코드 보기의 링크) |

- 전송은 `basic`과 `tus`를 지원한다. batch의 `transfers`에 둘 다 없으면 422이다. 올리기에서 클라이언트가 `tus`를 알렸으면 `tus`를 고르고(아래 `tus`), 그 밖에는 `basic`이다. 내려받기는 항상 `basic`이다.
- 응답의 미디어 타입은 `application/vnd.git-lfs+json`이고 오류는 `{"message": …}`이다. 객체별 오류는 `error: {code, message}`(404, 413, 422).

## 3. 인증과 권한

- **git HTTP와 똑같다**: 개인용 토큰만 받고 비밀번호는 거부한다(D-70). 토큰 범위는 읽기 `repo`나 `read:repo`, 쓰기 `repo`(공개 프로젝트는 `public_repo`)이다. 프로젝트 권한은 코드 읽기와 푸시 권한이고 `isCodeAccessibleMemberOnly` 규칙도 같다. 공개 프로젝트는 익명도 내려받는다. OAuth 토큰은 받지 않는다.
- 인증이 없으면 401과 `WWW-Authenticate: Basic realm="Yobi"`, 로그인했는데 권한이 없으면 403이다. 없는 프로젝트, LFS를 지원하지 않는 저장소(Subversion), 사이트 설정이 꺼진 경우는 404이다. 주소 인증 실패 제한과 지표(`vcs=git_lfs`)는 git HTTP와 같은 것을 쓴다.
- **SSH**: 클라이언트가 SSH로 `git-lfs-authenticate '<경로>' <download|upload>`를 보내면 서버가 `{"href": …/info/lfs, "header": {"Authorization": "Bearer yona_lfs_…"}, "expires_in": 600}`를 돌려준다. **객체 전송은 HTTP로 한다.** 토큰은 사용자, 프로젝트, 동작(내려받기 또는 올리기), 만료에 묶인 HMAC-SHA256 서명 값(키는 사이트 비밀 키 `application.secret`)이고 LFS 경로에서만 유효하며 프로젝트 권한 검사는 그대로 한다. 내장 sshd와 Go 도우미 중계 모두 같은 명령 처리기를 거친다.

### 3.1 `tus`(이어올리기)

`lfs.tustransfers`를 켠 클라이언트가 batch에 `tus`를 알리면(`"transfers":["basic","tus",…]`, 실제 클라이언트는 `basic`을 앞에 두고 `tus`를 덧붙인다) 서버는 올리기에 `tus`를 고르고 동작 주소를 `…/info/lfs/tus/{oid}`로 준다. **batch가 올릴 객체마다 업로드를 미리 만든다**: 실제 클라이언트는 따로 만들지 않고 곧바로 `HEAD`로 위치를 묻기 때문이다. 그 뒤 `HEAD`(받은 위치 `Upload-Offset`, 전체 `Upload-Length`)와 `PATCH`(`application/offset+octet-stream`, 위치부터 이어 받기)를 쓰며, 위치가 틀리면 409, 길이를 넘으면 413, 다 받았는데 해시가 다르면 422(받은 조각을 버림)이다. 받는 중인 조각은 `repo/lfs/<소유자>/<이름>/tus/<oid>.part`에 있다. `POST`(만들기, tus 1.0.0 `creation`)도 규격대로 받는다. **`OPTIONS`는 지원하지 않는다**(이 경로에 매핑해도 스프링이 정적 자원 처리기로 넘겨 404였고 실제 클라이언트가 쓰지 않는다). 한도와 권한은 기본 전송의 올리기와 같다.

## 4. 한도와 설정

사이트 관리 "Git LFS"(`/sites/lfs`): 사용(기본 켬), 객체 하나의 최대 크기(기본 2 GiB, MiB 단위 입력), 프로젝트별 저장 한도(기본 0=제한 없음). 설정은 데이터베이스(`site_setting`)에 저장하고 바꾸면 감사 `site.lfs_changed`가 남는다. 최대 크기를 넘는 객체는 batch에서 413 오류이고, PUT은 읽다가 멈춘다(임시 파일 없음). 프로젝트 한도는 같은 batch 안의 올리기를 누적해서 센다.

## 5. 수명 주기와 포크

- **개명, 소유자 이전**: 저장소를 옮기는 자리(`RepositoryService.moveRepository`)에서 LFS 디렉터리도 옮긴다. 옛 이름 주소는 프로젝트를 먼저 찾아(`findByPreviousPlaceOf`) 현재 이름의 저장 위치를 쓰므로 동작한다.
- **삭제**: `repositories.deleteLfs(project)`가 디렉터리를 지우고 잠금도 지운다(`ProjectDeletionService`). 사용자를 지우면 그 사용자의 잠금도 지운다. 사이트 데이터 가져오기 뒤 고아 잠금 행을 정리한다.
- **포크**: 읽기는 자기 저장소에 없으면 원본(과 그 원본의 원본, 최대 16단계)에서 빌려 쓴다. 올리기 batch도 원본에 있으면 "이미 있음"으로 본다. 쓰기는 자기 저장소에 한다. **원본을 지우면** 포크가 빌려 쓰던 객체를 포크로 가져간다(같은 파일 시스템이면 하드 링크, 아니면 복사).

## 6. 잠금

테이블 `lfs_lock`(프로젝트와 경로 해시가 유일). 이미 잠긴 경로는 409와 기존 잠금, 푸는 것은 잠근 사람이고 `force`는 프로젝트 관리자만 한다. 경로는 비었거나 절대 경로이거나 `..`이 있거나 1000자를 넘으면 422이다. 목록은 `path`와 `id`로 거르고 `cursor`, `limit`(최대 100)로 쪽을 나눈다. 감사 `lfs.lock_created`, `lfs.lock_released`(강제는 `details.force`).

## 7. 화면

- 코드 보기: LFS 포인터 파일이면 원래 포인터 텍스트는 그대로 두고 위에 "Git LFS로 저장된 파일(크기)"과 내려받기 링크를 더한다(서버에 객체가 없으면 없다고 알린다).
- 프로젝트 설정 "Git LFS" 탭: 사용량(객체 수, 용량, 한도), 잠금 목록, 프로젝트 관리자의 강제 풀기.
- 사이트 관리 "Git LFS": 설정과 전체 사용량.

## 8. 검증

시험: `LfsStorageTest`, `LfsApiTest`(batch, 전송, 권한, 한도), `LfsLockServiceTest`, `LfsLockApiTest`, `LfsLifecycleTest`(개명, 이전, 삭제, 포크), `LfsSshTest`(명령, 토큰, 토큰 사용), `LfsPointerTest`, `LfsCodeBrowserTest`, `LfsProjectSettingTest`, `SiteLfsTest`. **실제 클라이언트 확인(단계 0)**: 공식 `git-lfs` 3.8.1을 격리된 폴더에 내려받아 실제 jar에 대해 실행했다. HTTP: 주소 탐색(`.git/info/lfs`), 개인용 토큰 인증, 6 MiB 업로드와 내려받기(해시 일치), 이미 있는 객체 건너뛰기, `lock`, `locks`, `locks --verify`, `unlock`, `Range` 206, 토큰 없는 비공개 복제 거부. 내장 SSH 서버(실제 `ssh` 클라이언트): `git-lfs-authenticate`, 업로드, 복제와 내려받기, 잠금과 해제. **이 확인에서 규격을 기억에 의존해 틀렸던 두 곳을 고쳤다**(아래 9절). `tus` 이어올리기(실제 클라이언트가 2 MiB까지 올라가 있던 6 MiB 객체를 그 위치부터 이어 올려 해시 일치). 시스템 OpenSSH 중계는 실제 sshd 없이, sshd가 하는 일을 흉내 내는 스크립트와 셸 훅, Go 도우미(`yona-ssh`)로 `git-lfs-authenticate`를 확인했다(`SshRelayServerTest`, `SshRelayGoHelperTest`). 중계는 거부 사유를 종료 코드로 알릴 수 없어, `git-lfs-authenticate`의 거부는 pkt-line 접두어 없이 글로 쓴다.

## 9. 실제 클라이언트가 알려 준 것

1. **batch 응답에 `authenticated: true`를 주면 안 된다.** 이 값이 참이면 클라이언트는 그 동작 주소에 자격을 보내지 않는다(미리 서명한 주소용). Basic 인증을 쓰는 Yona는 생략해야 같은 자격이 객체 전송에도 붙는다. 처음에는 참으로 주었다가 PUT이 401을 반복했다.
2. **SSH 토큰은 batch 응답의 동작마다 `header`로 되돌려 줘야 한다.** 클라이언트는 `git-lfs-authenticate`가 준 헤더를 batch와 잠금 요청에는 붙이지만 객체 전송(PUT, GET)과 verify에는 붙이지 않는다. 토큰(`Bearer yona_lfs_…`)으로 온 요청에는 동작의 `header`에 같은 `Authorization`을 실어 준다.
3. **git-lfs 3.x는 SSH에서 순수 SSH 전송(`git-lfs-transfer`)을 먼저 시도한다.** 서버가 모르는 명령이면 "Invalid command" 오류를 받고 `git-lfs-authenticate`로 내려간다. 동작에는 문제가 없고 클라이언트의 추적 로그에 실패 메시지가 한 번 남는다. 순수 SSH 전송 지원은 추가 논의 항목이다.
4. 복제할 때 LFS 객체를 받으려면 사용자 쪽에 `git lfs install`(전역 또는 저장소)이 되어 있어야 한다(서버와 무관한 클라이언트 설정).

5. **`basic`을 앞에 두고 `tus`를 덧붙이는 클라이언트의 목록에서 앞에서부터 고르면 `tus`가 영영 선택되지 않는다.** 올리기에서는 `tus`가 있으면 우선한다.

## 10. 남은 일

- 추가 논의 항목: 순수 SSH 전송(`git-lfs-transfer`), 쓰이지 않는 객체와 끊긴 `tus` 조각의 정리(지금은 프로젝트를 지울 때만 지운다), Mercurial 주소 규칙(X-7에서 실제 `hg`로 확인), 잠금 목록 고도화([decisions.md](../decisions.md) "재결정 대기" 절).
