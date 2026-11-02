---
title: SSH 접근과 SSH 키, 배포 키 설계
type: architecture
sources: [yona-v2-wiki/decisions.md D-66]
updated: 2026-11-02
---

# SSH 접근, SSH 키, 배포 키 (X-3, D-66)

**상태: 구현 완료(2026-11-02, 내장 SSH 서버와 시스템 sshd 연동).** 결정과 이유는 [decisions.md D-66, D-67](../decisions.md), 구현 판단은 A-323부터 A-325이다. v1.6에는 없는 추가 항목이며 `yona.ssh.enabled`와 `yona.ssh.relay.enabled`를 켜지 않으면 포트도 소켓도 열지 않고 화면과 동작이 v1.6과 같다.

## 한눈에 보기

| 영역 | 내용 |
|---|---|
| 키 등록 | 사용자 설정의 "SSH 키" 탭과 프로젝트 설정의 "배포 키" 화면. 둘 다 **목록 화면**(삭제 버튼 하나)과 **추가 화면** 두 개로 나눈다(D-65 8번을 따른다). 배포 키 추가 화면에는 "쓰기 허용" 체크가 하나 더 있다 |
| 저장 | 새 테이블 `ssh_key`(사용자), `deploy_key`(프로젝트). 지문(`SHA256:...`)은 두 테이블을 합쳐 사이트 전체에서 유일하다 |
| 서버 | Apache MINA SSHD 2.15. `yona.ssh.enabled=true`일 때만 앱 안에서 뜬다. 공개키 인증만 받고 셸, SFTP, 포트 전달은 열지 않는다 |
| 명령 | 저장소 종류별 처리기(`SshVcsHandler`). 지금은 git(`git-upload-pack`, `git-receive-pack`)뿐이고 Mercurial(`hg -R <저장소> serve --stdio`)은 X-7이 같은 자리에 더한다(D-66 6번) |
| 인가 | HTTP와 같은 접근 규칙(`AccessControl`). 배포 키는 자기 저장소만 접근하고 읽기 전용이면 쓰지 못한다 |
| 푸시 | HTTP와 같은 JGit과 같은 푸시 훅(`GitHooks`)이라 브랜치 보호 규칙, 서명 커밋 필수 규칙, 풀 리퀘스트와 웹훅 처리가 똑같이 적용된다 |
| REST | `/user/ssh_keys`, `/users/{user}/ssh_keys`, `/owners/{owner}/projects/{project}/deploy_keys` |
| 감사와 지표 | `ssh_key.*`, `deploy_key.*`, SSH 푸시의 `git.push`(행위자와 주소), `auth.basic_failure`(채널 `ssh`), `yona.vcs.*`, `yona.security.events{channel=ssh}` |

## 두 방식

| | 내장 SSH 서버 | 시스템 sshd 연동(X-3b) |
|---|---|---|
| 켜는 설정 | `yona.ssh.enabled` | `yona.ssh.relay.enabled` |
| 접속을 받는 것 | Yona(Apache MINA SSHD) | 시스템 OpenSSH sshd(보통 22번 포트) |
| 주소 | `ssh://git@호스트:2222/소유자/이름.git` | `git@호스트:소유자/이름.git` |
| 설치 | 없음 | 훅과 계정과 sshd 설정 ([가이드](../../docs/yona-ssh-system-sshd.md)) |
| 인가와 푸시 훅 | 같다(`SshCommandRunner`, `SshAccess`, `GitSshHandler`) | 같다 |

## 시스템 sshd 연동 (X-3b, D-67)

sshd의 `AuthorizedKeysCommand` 훅(`tools/ssh/yona-ssh-auth.sh`)이 접속자의 공개키를 Yona의 **내부 인증 주소**(`POST /internal/ssh/authenticate`, 루프백과 공유 비밀 값 헤더 요구)에 물으면, Yona는 등록된 사용자 키나 배포 키일 때 **서명한 인증 표**(`v1.<종류>.<번호>.<만료>.<HMAC-SHA256>`, 5분 유효)를 돌려준다. 훅은 표를 심은 강제 명령(`command=`)이 든 `authorized_keys` 한 줄을 인쇄하고, 세션이 시작되면 그 명령이 `socat`으로 Yona의 **중계 소켓**(`SshRelayServer`, 유닉스 도메인 소켓, `rw-rw----`)에 연결해 세 줄(표, 클라이언트 주소, 원본 명령)을 보낸 뒤 입출력을 그대로 이어 붙인다. 중계는 표를 확인하고 내장 서버와 같은 `SshCommandRunner`로 명령을 인가하고 실행한다. 거부 사유는 프로토콜 시작 전에 git의 오류 줄(pkt-line `ERR`)로 보내 git이 `fatal: remote error: <사유>`로 보여 준다. 훅은 모르는 키나 오류에서는 아무것도 인쇄하지 않아 sshd가 인증을 거부한다.

진짜 OpenSSH 8.0 sshd(AlmaLinux 8 컨테이너)로 훅, 소켓, 복제, 푸시, 미등록 키 거부, 셸 거부를 확인했다. 자동 시험(`SshRelayServerTest`)은 sshd 자리에 `command=`를 실행하는 작은 스크립트를 두고 실제 훅과 `git`, `socat`으로 돌린다. 주소 단위 실패 한도는 내부 인증 주소에 적용하지 않는다(sshd가 키를 하나씩 물어 정상 접속도 실패로 센다. 시도 제한은 sshd의 `MaxAuthTries`와 fail2ban 몫이다).

## 설정 (`yona.ssh.*`)

| 키 | 기본값 | 설명 |
|---|---|---|
| `yona.ssh.enabled` | `false` | 내장 SSH 서버를 켠다. 끄면 포트를 열지 않는다 |
| `yona.ssh.port` | `2222` | 받는 포트. 0이면 운영체제가 고른다(시험용). 22를 쓰려면 root 권한이나 포트 전달이 필요하다 |
| `yona.ssh.bind-address` | 빈 문자열 | 묶는 주소. 비우면 모든 주소 |
| `yona.ssh.host-key-path` | 빈 문자열 | 호스트 키 파일. 비우면 데이터 디렉터리의 `ssh/ssh_host_ed25519_key`이며 처음 시작할 때 만든다 |
| `yona.ssh.user` | `git` | 접속 사용자 이름. 키로 사용자를 알아내므로 모두 같다(GitHub 방식) |
| `yona.ssh.public-port` | `0` | 복제 주소에 보일 포트. 0이면 실제 포트(시스템 sshd 방식만 켰으면 22). 컨테이너나 포트 매핑 뒤에서 바깥 포트가 다를 때 쓴다 |
| `yona.ssh.relay.enabled` | `false` | 시스템 sshd 연동을 켠다 |
| `yona.ssh.relay.socket-path` | 빈 문자열 | 중계 소켓. 비우면 `<데이터>/ssh/relay.sock` |
| `yona.ssh.relay.internal-secret`, `internal-secret-path` | 빈 문자열 | 훅과 나누는 공유 비밀 값(설정 또는 파일) |

모든 설정은 기본값 그대로 `application.yml`의 `yona.ssh`에 설명과 함께 적어 두었다.

호스트 키는 재시작해도 같아야 클라이언트가 "호스트 키가 바뀌었다"는 경고를 내지 않는다. 데이터 디렉터리를 옮길 때 `ssh/`도 함께 옮긴다. 시작할 때 호스트 키 지문을 로그(`SSH server started on port ...`)에 남긴다.

## 키 읽기 (`SshKeyParser`)

OpenSSH 공개키 한 줄(`~/.ssh/id_*.pub`)을 MINA의 `PublicKeyEntry`로 읽고 지문은 `KeyUtils.getFingerPrint`로 만든다(`ssh-keygen -lf`의 출력과 같다. 시험이 실제 `ssh-keygen`과 비교한다).

- 받는 종류: `ssh-ed25519`, `ssh-rsa`(2048비트 이상), `ecdsa-sha2-nistp256/384/521`, `sk-ssh-ed25519@openssh.com`, `sk-ecdsa-sha2-nistp256@openssh.com`.
- 거부(이유 코드): 비었음 `empty`, 형식 아님과 본문이 깨짐과 종류 표시와 본문 안의 종류가 다름과 `authorized_keys`의 옵션 접두(`command="..."` 등) `malformed`, 비밀키(OpenSSH와 PuTTY 형식) `private_key`, 여러 줄 `multiple_keys`, DSA `unsupported`, 2048비트 미만 RSA `weak`.
- 저장하는 공개키는 `종류 본문` 한 줄이다(주석은 이름 기본값으로만 쓰고 저장하지 않는다).
- 등록 거절: 이름 100자 초과, 사용자당(저장소당) 50개 초과, 지문 중복(사용자 키와 배포 키를 통틀어 사이트 전체).

## 접속과 인가 (`SshAccess`)

1. **인증**: 사용자 이름이 `yona.ssh.user`가 아니면 거부. 접속한 공개키의 지문으로 `ssh_key`, 그다음 `deploy_key`를 찾는다. 잠기거나(`LOCKED`) 삭제된(`DELETED`) 사용자의 키는 인증하지 못한다.
2. **명령 읽기**: 인증한 접속의 명령 줄을 처리기들이 차례로 읽는다. 알 수 없는 명령과 셸 요청은 `ERROR: Invalid command.`로 거부한다. git 경로는 `'/소유자/저장소.git'`, `'소유자/저장소'` 같은 모양을 받고 두 단계가 아니거나 `..`, 공백, 셸 메타 문자가 있으면 거부한다.
3. **인가**(명령마다 다시 읽으므로 접속한 뒤 사용자가 잠기거나 키가 지워져도 그 뒤 명령은 막힌다): 읽을 수 없는 저장소는 있는지 없는지 알리지 않으려고 `ERROR: Repository not found.`이다. 읽을 수 있는데 쓸 수 없으면 `ERROR: Permission to 소유자/저장소 denied to 로그인ID.`이고, 읽기 전용 배포 키의 푸시는 `ERROR: The deploy key for 소유자/저장소 is read-only.`이다.
   - 사용자 키: 읽기는 읽기 권한, 쓰기는 수정 권한이며 코드를 구성원에게만 여는 프로젝트는 구성원이 아니면 수정 권한을 요구한다([GitHttpController](../../src/main/kotlin/dev/yona/web/git/GitHttpController.kt)와 같은 판정).
   - 배포 키: 키가 속한 저장소만 읽는다(공개 저장소여도 다른 저장소는 `Repository not found`). 푸시는 읽기 전용이 아닐 때만.
4. **실행**: 허용되면 `GitSshHandler`가 JGit `UploadPack`/`ReceivePack`을 실행한다. 푸시 훅(`preReceive`, `postReceive`)은 HTTP와 같다. 연결이 양방향 파이프라 요청과 응답을 한 연결에서 주고받는다.

배포 키로 푸시하면 푸시한 사용자가 없다(`sender`가 `null`). 사용자가 필요한 푸시 뒤 작업(이슈 번호 참조, 풀 리퀘스트 갱신과 자동 병합 확인, 커밋 알림)은 건너뛴다(A-324).

## 운영

- **인증 실패 한도**: HTTP 토큰 인증과 같은 `TokenFailureLimiter`(`yona.api-token-failure-limit`, `yona.api-token-block-minutes`)를 주소 기준으로 쓴다. 접속 하나에서 키를 여러 개 시도해도 실패는 한 번만 센다. 성공하면 횟수를 지운다. 막힌 주소는 등록한 키로도 접속하지 못한다.
- **감사**: 웹 요청이 없는 스레드라 `AuditRecorder.withRemote(행위자, 주소, "ssh")`로 행위자, 접속 주소, 접속 종류(`userAgent` 열에 `ssh`)를 정한다. 사용자 키는 `USER`, 배포 키는 `DEPLOY_KEY`(번호와 키 이름)이다. 인증하지 못한 채 끝난 접속은 접속이 닫힐 때 `auth.basic_failure`(채널 `ssh`, 이유 `unknown_key`나 `invalid_user`)로 남기고, 차단은 접속마다 한 번 `auth.token_blocked`로 남긴다.
- **지표**: `yona.vcs.request{vcs=git, service=upload_pack|receive_pack, target=code, result}`(결과는 `ok`, `denied`, `not_found`, `error`), `yona.vcs.transfer`, 푸시의 `yona.vcs.pushes`. HTTP와 달리 `info_refs`가 없다. 보안 지표의 채널 `ssh`.
- **복제 주소**: 서버를 켠 사이트의 프로젝트 홈에 HTTP/SSH 선택 단추가 생긴다. 포트가 22이면 `git@호스트:소유자/이름.git`, 그 밖이면 `ssh://git@호스트:포트/소유자/이름.git`이다. 서버를 켜지 않으면 복제 상자는 v1.6과 같다.

## 설정 화면과 REST

화면: `GET /user/editform/ssh`(목록), `/user/editform/ssh/new`(추가), `POST /user/ssh`, `POST /user/ssh/{id}/delete`. 프로젝트: `GET /{owner}/{project}/setting/deploykeys`(목록), `.../new`(추가), `POST .../deploykeys`, `POST .../deploykeys/{id}/delete`. 목록의 행 동작은 삭제 하나이고 편집과 상세는 없다. 배포 키는 프로젝트 관리자만, Git 프로젝트에서만 쓴다.

| REST | 범위 | 설명 |
|---|---|---|
| `GET /-_-api/v1/user/ssh_keys`, `/{id}` | `read:ssh_key` | 내 키 목록과 한 건 |
| `POST /-_-api/v1/user/ssh_keys` | `write:ssh_key` | `{name, publicKey}`. 받을 수 없는 키 400, 이미 등록된 키 409, 201 |
| `DELETE /-_-api/v1/user/ssh_keys/{id}` | `admin:ssh_key` | 204 |
| `GET /-_-api/v1/users/{user}/ssh_keys` | `read:user` | 다른 사용자의 공개키(키 이름은 숨김) |
| `GET /-_-api/v1/owners/{owner}/projects/{project}/deploy_keys`, `/{id}` | `read:deploy_key` | 프로젝트 관리자만 |
| `POST .../deploy_keys` | `write:deploy_key` | `{name, publicKey, readOnly}`. `readOnly`를 생략하면 참 |
| `DELETE .../deploy_keys/{id}` | `admin:deploy_key` | 204 |

범위는 `admin:`이 `write:`와 `read:`를, `write:`가 `read:`를 포함한다. 배포 키는 범위와 사용자의 실제 권한(프로젝트 관리자)의 교집합이다.

## 사이트 데이터와 삭제

`ssh_key`, `deploy_key`는 사이트 내보내기와 가져오기(`DataTables`)에 들어 있다. 옛 파일(테이블 없음)을 가져오면 남은 키는 비우고 없는 사용자와 프로젝트를 가리키는 행은 지운다. 사용자를 삭제 처리하면 그 사용자의 SSH 키를, 프로젝트를 지우면 그 프로젝트의 배포 키를 함께 지운다.

## 토큰 범위 열

범위가 늘어 전체 범위 문자열이 255자를 넘으므로 `api_token.scopes`, `device_authorization.scopes`, `audit_log.token_scopes`를 1000자로 넓혔다. `ddl-auto: update`는 이미 있는 열을 넓히지 못해 `SchemaColumnWidener`가 시작할 때 좁은 열을 찾아 `alter`한다(H2, PostgreSQL, MySQL/MariaDB, MSSQL, Cubrid 문법). 실패해도 시작을 막지 않고 경고만 남긴다(A-323).

## 한계

- 포트 22를 쓰려면 내장 서버는 root 권한이나 포트 전달이 필요하다. 시스템 sshd 연동을 쓰면 sshd가 22번 포트를 받는다.
- SSH 키로 한 커밋 서명은 검증하지 않는다(`ssh_key`의 지문과 공개키 형식은 맞춰 두었다).
- 위키 저장소와 Subversion, Mercurial은 SSH로 접근하지 못한다. Mercurial은 X-7이 처리기를 더한다.
- 프로젝트 홈의 복제 상자만 SSH 주소를 보인다. 빈 저장소 안내 화면(`code/nohead`)은 HTTP 명령을 그대로 보인다.
- 배포 키의 푸시는 푸시한 사용자가 없어 풀 리퀘스트 자동 갱신, 이슈 참조, 커밋 알림이 일어나지 않는다.
- 접속하는 동안 도중에 한도를 넘은 동시 접속 수를 제한하지는 않는다(MINA의 기본 대기 시간만 쓴다).
