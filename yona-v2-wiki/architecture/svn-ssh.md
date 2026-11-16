---
title: Subversion SSH 접근 (svn+ssh)
type: architecture
sources: [Subversion notes/ra-svn(프로토콜 문서), 실제 svnserve 1.14.5와 svn 1.14.5 클라이언트의 대화 녹화, src/main/kotlin/dev/yona/ssh/svn, src/main/kotlin/dev/yona/ssh/SvnSshHandler.kt]
updated: 2026-11-16
---

# Subversion SSH 접근 (X-55, A-615)

**상태: 구현 완료(2026-11-16, `ra-svn` 브랜치).** v1.6에는 없는 추가 개발 항목이다. 켜는 방법은 Git, Mercurial과 같다: `yona.ssh.enabled=true`(내장 SSH 서버, [ssh.md](ssh.md)). 서버에 Subversion 패키지(`svnserve`)가 필요 없다.

## 쓰는 법

```bash
svn checkout svn+ssh://git@호스트/소유자/프로젝트
svn commit -m "메시지"
```

- 사용자 이름은 Git SSH와 같이 항상 `git`이고, 사용자는 접속한 **SSH 키**로 정해진다(등록한 사용자 키나 배포 키). 사용자 설정의 "SSH 키" 탭에 공개키를 올린다.
- 주소의 경로는 `/소유자/프로젝트`이다(HTTP 주소의 `/svn/...` 접두어가 없다). 포트가 22가 아니면 `ssh -p`를 `SVN_SSH` 환경 변수나 `~/.subversion/config`의 `[tunnels]`에 준다. `svn`은 사용자 지정 `SVN_SSH`에서는 주소의 포트(`svn+ssh://git@호스트:2222/...`)를 `ssh -p`로 넘기지 않고 호스트 이름에 그대로 붙인다.

## 권한

Git SSH와 같은 규칙이다(`SshAccess`, HTTP와 같은 `AccessControl`).

| 접속 | 읽기(`checkout`, `update`, `log`, `cat`, `diff`, `blame`) | 쓰기(`commit`, `lock`) |
|---|---|---|
| 프로젝트 구성원 | 된다 | 된다 |
| 공개 프로젝트의 다른 등록 사용자 | 된다 | 거절(`Authorization failed`) |
| 비공개 프로젝트의 비구성원 | 없는 것처럼 `Repository not found.` | - |
| 읽기 전용 배포 키 | 자기 저장소만 된다 | 거절 |
| 쓰기 배포 키 | 자기 저장소만 된다 | 된다 |
| 등록하지 않은 키 | SSH 인증에서 거절 | - |

커밋의 작성자(`svn:author`)는 접속한 사용자의 로그인 아이디이고, 배포 키는 `deploy-key-<번호>`이다. 사용자를 잠그거나 키를 지우면 그 뒤의 명령부터 인가되지 않는다.

## 왜 직접 구현했는가 (A-615)

`svn+ssh`는 클라이언트가 원격에서 `svnserve -t`를 실행하고 표준 입출력으로 `ra_svn` 프로토콜을 주고받는 방식이다. 선택지는 둘이었다.

| | 방식 | 평가 |
|---|---|---|
| A | 시스템 `svnserve -t`를 감싼다. 첫 메시지만 읽어 인가한 뒤 `svnserve`로 넘긴다 | 프로토콜 구현이 필요 없다. 서버에 Subversion 패키지가 있어야 하고, 읽기 전용 여부를 프로세스를 띄울 때 정해야 한다. 에이전트의 권장안 |
| **B** | **`ra_svn` 서버를 Kotlin으로 직접 구현한다(SVNKit이 저장소를 읽고 쓴다)** | 외부 프로그램이 필요 없다. 프로토콜 구현과 시험의 비용이 크다. **선생님이 선택하셨다(2026-11-16, 브랜치 `ra-svn`)** |

명령 줄에 저장소가 없고(`svnserve -t`뿐이다) 프로토콜의 첫 메시지(클라이언트가 보내는 주소)에 들어 있으므로, SSH 명령 실행기에 "저장소를 나중에 아는 요청"(`SshRepositoryRequest.deferred`)을 더했다. 처리기가 첫 메시지에서 저장소를 알아낸 뒤 `SshDeferredContext.authorize`로 같은 인가를 한다. Mercurial의 "쓰기가 선택인 읽기"(`writeOptional`)와 같은 자리이다.

## 구조

| 파일 | 역할 |
|---|---|
| `ssh/SvnSshHandler` | `svnserve -t` 명령을 받아 `RaSvnServer`를 돌린다. 주소의 앞 두 단계(`소유자/프로젝트`)를 저장소로 보고 인가한다 |
| `ssh/svn/RaSvnWire` | 프로토콜 항목(숫자, 단어, 문자열, 목록)을 읽고 쓰는 부호기. 문자열은 64MB까지, 선언한 길이만큼 미리 메모리를 잡지 않고 받은 만큼만 읽는다 |
| `ssh/svn/RaSvnServer` | 인사, 인증(터널에서 이미 인증한 사용자를 `EXTERNAL`로 받는다), 명령 반복문, 오류 응답 |
| `ssh/svn/RaSvnCommands` | 명령 처리(아래 표). SVNKit이 저장소에 실행한다 |
| `ssh/svn/RaSvnEditorWriter` | SVNKit의 편집기 호출을 `ra_svn` 편집기 명령으로 바꿔 클라이언트에 보낸다(`update`, `diff`, `status`, `switch`) |
| `ssh/svn/RaSvnEditorReader` | 커밋하는 클라이언트가 보내는 편집기 명령을 모아 SVNKit 커밋 편집기에 올바른 순서로 재생한다. 파일 내용은 임시 파일에 쌓는다 |
| `ssh/svn/RaSvnFileSpool` | `get-file`의 내용을 4MB까지 메모리, 넘으면 임시 파일에 두고 MD5를 같이 계산한다 |

저장소를 읽고 쓰는 일은 모두 SVNKit(`FSRepository`)이 하고, 이 코드는 프로토콜을 SVNKit 호출로 옮기는 것뿐이다.

## 지원하는 명령

| 영역 | 명령 |
|---|---|
| 읽기 | `get-latest-rev`, `check-path`, `stat`, `list`, `get-dir`, `get-file`, `log`, `get-iprops`, `get-locations`, `get-location-segments`, `get-file-revs`(blame), `get-mergeinfo`, `get-deleted-rev`, `rev-proplist`, `rev-prop`, `reparent` |
| 작업 복사본 | `update`(체크아웃, export 포함), `status`(`-u`), `diff`, `switch`. 깊이(`--depth`, `--set-depth`, 희소 체크아웃)를 지원한다 |
| 쓰기 | `commit`(추가, 수정, 삭제, 디렉터리, 복사, 이동, 속성, 큰 파일, 잠금 토큰), `change-rev-prop`, `change-rev-prop2`(리비전 속성 변경, 아래 규칙) |
| 잠금 | `lock`, `unlock`, `lock-many`, `unlock-many`, `get-lock`, `get-locks` |
| 미러링 | `replay`, `replay-range`(`svnsync`, 하위 디렉터리만 미러링하는 `partial-replay` 포함) |

시스템 `svnserve`가 처리하는 명령은 모두 처리한다. 인사에서 `svndiff1`과 `accepts-svndiff2`는 내놓지 않아 클라이언트가 압축하지 않은 svndiff0을 쓴다(구현이 단순하고 호환성이 가장 넓다). 서버가 내놓는 기능은 `edit-pipeline`, `absent-entries`, `commit-revprops`, `depth`, `log-revprops`, `atomic-revprops`, `partial-replay`, `inherited-props`, `list`이다.

**리비전 속성 변경의 권한(A-616)**: 시스템 `svnserve`는 저장소의 `pre-revprop-change` 훅이 허용해야 리비전 속성(커밋 로그 등)을 바꾼다. Yona의 저장소에는 그 훅이 없어서 훅 검사를 건너뛰고 Yona가 판정한다. 쓸 수 있는 접속(구성원, 쓰기 배포 키)만 바꿀 수 있고, `svn:author`와 `svn:date`는 바꾸지 못한다(`Authorization failed`). `post-revprop-change` 훅은 저장소에 있으면 실행한다. 읽기 전용 접속은 `svnsync` 같은 읽기 쪽 명령(`replay`)은 할 수 있다.

**미러링 대상 저장소**: `svnsync`의 대상 저장소는 보통 `svnadmin`으로 만든 별도 저장소이며 `pre-revprop-change` 훅이 필요하다(대상은 Yona가 아니라 `svnsync`를 실행하는 쪽이다).

## 어떻게 확인했는가

프로토콜의 바이트 하나가 틀려도 `svn`이 멈추므로 기억이나 문서가 아니라 **실제 대화**를 기준으로 삼았다.

1. 진짜 `svnserve -t`와 `svn` 클라이언트 사이에 기록용 터널(파이썬)을 끼워 명령마다 요청과 응답의 바이트를 녹화했다. 이 형식으로 부호기의 시험 기대값(`RaSvnWireTest`)과 각 명령의 응답을 맞췄다.
2. `svn`이 보내지 않는 명령(`change-rev-prop`, `get-deleted-rev`)은 진짜 `svnserve`에 직접 보내서 응답을 얻었다(오류 코드 160049, 150003 포함). 이 응답을 시험의 기대값으로 썼다. 가짜 서버가 아니라 `RaSvnTestServer.RawSession`으로 같은 형식을 확인한다.
3. 명령마다 같은 저장소를 `file://`로 실행한 결과와 `svn+ra://`로 실행한 결과가 같아야 한다는 시험을 썼다(`info`, `ls`, `log -v`, `diff`, `blame`, 체크아웃한 작업 복사본의 파일과 속성과 상태까지). 서버 시험은 TCP 터널로, 접근 규칙 시험(`SvnSshServerTest`)은 실제 `ssh`와 내장 SSH 서버로 한다.
4. 시험 도구가 서버의 멈춤을 가려내도록 `svn` 호출에 60초 제한을 두고, 실패하면 클라이언트와 서버의 대화를 오류에 붙인다(`RaSvnTestServer.dialogue`).

## 알려진 한계와 주의

- **시스템 sshd 중계 방식(X-3b)도 지원한다.** 같은 `SshCommandRunner`를 거치고, 훅 스크립트가 만든 강제 명령으로 `svn`이 읽기, 커밋(권한 없는 사용자 거절), 비공개 저장소 거절을 하는 시험이 있다(`SshRelayServerTest`). 인증 표가 틀리면 git의 오류 줄 대신 `ra_svn`의 실패 응답(170001)으로 거절해 `svn`이 읽을 수 있게 했다. 진짜 sshd로는 확인하지 않았고 sshd 역할을 하는 스크립트로 확인했다(Git, Mercurial 시험과 같은 방식).
- **커밋 뒤 후속 처리가 없다.** WebDAV 커밋에도 알림, 웹훅, 감사 같은 후속 처리가 없어서(`SvnDavFilter`, `SvnDavService` 확인) 같게 두었다.
- 한 커밋의 파일 내용은 임시 파일에 쌓은 뒤 저장소에 재생하므로 커밋 하나가 끝날 때까지 서버의 임시 디렉터리에 그만한 공간이 필요하다. 클라이언트가 디렉터리를 닫은 뒤 파일 내용을 보내서(녹화로 확인) 도착 순서대로 흘려보낼 수 없기 때문이다.
- 프로젝트 홈의 복제 주소 상자와 빈 저장소 안내에 HTTP와 SSH를 고르는 단추가 있다(Git, Mercurial과 같다). SVN의 SSH 주소는 `svn+ssh://git@호스트[:포트]/소유자/이름`이다(`svn`에는 scp 형식이 없다). 빈 저장소 안내의 `--username`은 svn+ssh에서 무시된다(사용자는 SSH 키로 정해진다).
