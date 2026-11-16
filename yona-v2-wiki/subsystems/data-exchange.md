---
title: 데이터 내보내기와 가져오기
type: subsystem
sources: [app/data/DataService.java, app/data/DefaultExchanger.java, app/data/exchangers 아래 47개 클래스(v2는 추가 항목의 테이블 5개를 더함, D-34 D-35 D-36 D-37), app/controllers/SiteApp.java:352-382, docs/ko/spec/export-and-import.md]
updated: 2026-10-29
---

# 데이터 내보내기와 가져오기

코드: `domain/site/DataTables`(83개 테이블 명세: v1.6의 47개와 추가 항목의 테이블 22개, 사용자 데이터 테이블 14개, 아래 "추가 항목의 테이블" 절과 [데이터 교정 스크립트와 알려진 한계](../architecture/data-fix-and-known-limits.md)의 R-56-2 절), `DataService`, `web/site/SiteDataController`.

## 파일 모양

최상위 JSON 객체에 테이블 이름(대문자, 사용자 테이블은 `N4USER`)마다 행의 배열이 있고, 시퀀스가 있는 테이블은 그 뒤에 `<이름>_SEQ`(다음 번호)가 이어진다. 필드는 테이블 이름 순서(문자열 비교)이다. 조인 테이블 9개(`COMMENT_THREAD_N4USER`, `ISSUE_COMMENT_VOTER`, `ISSUE_ISSUE_LABEL`, `ISSUE_VOTER`, `NOTIFICATION_EVENT_N4USER`, `PROJECT_LABEL`, `PULL_REQUEST_REVIEWERS`, `USER_ENROLLED_ORGANIZATION`, `USER_ENROLLED_PROJECT`)에는 시퀀스가 없다.

- 행의 키는 소문자 컬럼 이름이고 컬럼 순서는 `DataTables`의 목록과 같다. `ATTACHMENT.container_type`은 v1.6이 키를 `content_type`으로 썼다. v2는 `container_type`으로 쓰고 가져올 때 옛 `content_type` 키도 읽는다(R-56-8, A-372).
- 값: 정수와 `int`는 숫자, 문자열과 긴 문자열은 문자열, 시각은 에폭 밀리초(`ATTACHMENT.created_date`는 날짜의 밀리초), 불린은 `true/false`(DB NULL은 `false`). DB NULL은 JSON `null`이다. `ATTACHMENT.container_id`와 `PROJECT_TRANSFER.sender_id`는 DB는 정수이지만 JSON은 문자열이다.
- 엔티티에 있어도 v1.6 Exchanger가 다루지 않는 컬럼과 14개 테이블(즐겨찾기, 웹훅, 소셜 로그인 정보, 사용자 설정, 인증 요청 등)은 파일에 없다(R-56).

## 내보내기

한 연결로 테이블마다 `select`해 행을 바로 쓴다(`order by` 없음, 트랜잭션 격리 없음). 시퀀스 값은 MariaDB는 `AUTO_INCREMENT`, H2와 PostgreSQL은 가장 큰 번호에 1을 더한 값이다(A-92).

## 가져오기

1. 첫 토큰이 객체가 아니면 아무것도 하지 않는다.
2. 외래 키 검사를 끈다(H2 `SET REFERENTIAL_INTEGRITY FALSE`, PostgreSQL `session_replication_role = replica`(슈퍼유저 권한), MariaDB `FOREIGN_KEY_CHECKS = 0`와 `utf8mb4`). 끝나면 되돌린다.
3. 파일의 필드를 차례로 읽는다. 테이블 이름(대소문자 무시)이면 그 테이블을 `delete from`으로 비우고 행을 100개씩 배치로 넣는다. 테이블 단위 트랜잭션이며, 오류가 나면 그 테이블만 되돌리고 로그를 남기고 다음 테이블을 계속한다. 파일에 없는 테이블은 그대로이다. 모르는 필드는 건너뛴다.
4. `<이름>_SEQ`이면 다음 번호를 복원한다(H2 `alter column restart with`, PostgreSQL `setval`, MariaDB `auto_increment`).

값 바인딩(JSON `null`일 때): 정수는 DB가 NULL을 허용하는 컬럼이면 SQL NULL, 아니면 `0`(v1.6은 교환기가 NULL 허용으로 표시한 28개 컬럼만 NULL이었다, R-56-6, A-372), 문자열과 긴 문자열은 NULL(문자열이 아닌 값도 NULL), 불린은 `false`, 시각은 JSON이 숫자가 아니면(`null`) NULL이고 숫자 `0`은 에폭 0으로 가져온다(v1.6은 `0`도 NULL이었다). 옛 가져오기가 남긴 0은 [데이터 교정 스크립트](../architecture/data-fix-and-known-limits.md)로 되돌린다. 테이블 값이 배열이 아니면 비우지 않고 건너뛴다(R-84, A-370). 일부 테이블이 실패하면 가져오기 화면으로 돌아가 `site.data.import.failed`와 실패한 테이블 이름을 안내한다(R-56-4, A-372). 행에 키가 없으면 그 테이블이 실패한다. 결과(`DataImportResult`)에 성공과 실패한 테이블 이름이 담기지만 화면은 `/`로 이동할 뿐이다(R-56).

기존 데이터를 교체하므로 가져오기 전에 반드시 내보내기로 백업한다. Git 저장소와 첨부 파일 본문은 다루지 않는다.

## 추가 항목의 테이블 (69개 중 22개, 2026-11-03 기준)

v1.6 파일에는 없는 테이블이라 **없는 파일을 가져와도 된다.** 파일에 이 테이블이 없을 때의 동작은 테이블 종류에 따라 다르다. 사용자, 프로젝트, 조직 번호를 가리키는 테이블은 파일에 그 부모 테이블이 있는데 이 테이블이 없으면(번호가 바뀌어 엉뚱하게 붙을 수 있으므로) 남아 있던 행을 비우고, 어느 경우든 없는 부모를 가리키는 행은 지운다(`DataService`의 `clean…`). **사이트 설정 테이블은 파일에 없으면 지금 설정을 그대로 둔다.**

| 항목 | 테이블 |
|---|---|
| X-14, X-12, X-27 풀 리퀘스트 | `PULL_REQUEST_ASSIGNEE`, `PULL_REQUEST_ISSUE_LABEL`, `PULL_REQUEST_REVIEW`, `PULL_REQUEST_REVIEW_REQUEST` |
| X-5 토큰 | `API_TOKEN` |
| X-4, X-3 키 | `GPG_KEY`, `SSH_KEY`, `DEPLOY_KEY` |
| X-2 2단계 인증 | `TWO_FACTOR_CREDENTIAL`, `TWO_FACTOR_BACKUP_CODE`, `KNOWN_DEVICE`, `SITE_SETTING` |
| X-1 OAuth 앱 | `OAUTH_CLIENT`, `OAUTH_AUTHORIZATION`, `OAUTH_CONSENT` |
| X-39 조직 OAuth 앱 정책(2026-11-03에 더함) | `ORGANIZATION_OAUTH_POLICY`, `ORGANIZATION_OAUTH_APPROVAL` |
| X-8 Git LFS 잠금(2026-11-03에 더함) | `LFS_LOCK`(객체 파일은 데이터 폴더 `repo/lfs`에 있어 이 파일에 들어가지 않는다) |
| X-16, D-82 사내 로그인 설정(2026-11-03에 더함) | `LDAP_SETTINGS`, `OIDC_SSO_SETTINGS`, `SAML2_SSO_SETTINGS`(각각 한 행) |

- **비밀 값**: `OIDC_SSO_SETTINGS.client_secret`, `SAML2_SSO_SETTINGS.sp_private_key`와 `sp_previous_private_key`, `TWO_FACTOR_CREDENTIAL.secret_encrypted`는 사이트 비밀 키(`yona.secret`)로 암호화되어 있다. **다른 사이트로 옮길 때는 비밀 키를 같게 해야 풀린다.** 다르면 SSO 설정은 가져오지만 클라이언트 비밀 값을 다시 입력하고 SP 키를 다시 만들어야 한다.
- **내보내지 않는 것**: `audit_log`, `audit_chain_head`, `audit_stream_cursor`(감사 기록은 이 파일로 옮기지 않는다), `device_authorization`(잠깐 쓰는 값). 소셜 로그인과 SSO의 **연결 정보(`user_credential`, `linked_account`)는 v1.6처럼 내보내지 않는다.** 그래서 사이트를 옮기면 SSO 사용자는 다음 로그인 때 확인된 같은 이메일로 다시 연결된다.

## 실제 v1.6 내보내기 파일의 왕복 검증 (2026-11-16)

실제 v1.6 서버가 만든 내보내기 파일(`yona-diff-work/export-v1.6.json`, 85개 키, 30KB: 사용자 4, 프로젝트 5, 이슈 4, 이슈 댓글 3, 게시글 2, 알림 13, 라벨 154 등)을 지금의 v2(H2)에 가져온 뒤 다시 내보내 원본과 비교했다.

| 확인 | 결과 |
|---|---|
| 가져오기 | 오류 없이 끝남(303, 서버 로그에 오류 없음) |
| 테이블 | 원본의 85개 키가 모두 다시 나오고 **모든 테이블의 행 수가 같다**(손실 없음) |
| 값 | 같은 행의 값은 아래 네 가지를 빼고 모두 같다 |

값이 달라진 것은 모두 설명된다.
1. **v2가 새 컬럼을 채움**: `ISSUE.is_draft=false`, `weight=0`, `N4USER.is_guest=false`, `PROJECT`의 병합 방식 컬럼(병합 커밋만 허용), `PROJECT_MENU_SETTING.wiki=false`. v1.6 파일에 없던 컬럼의 기본값이다.
2. **v2가 더 많은 컬럼을 내보냄**: `ISSUE_COMMENT.parent_comment_id`, `POSTING.history/parent_id/updated_by_author_id`, `N4USER.english_name/token` 등. v1.6 교환기가 다루지 않던 컬럼이라 원본에는 키가 없고 값은 비어 있다(손실 아님, 키 차이).
3. **`*_SEQ` 값**: v1.6은 Ebean의 할당 단위(21, 41)를 썼고 v2는 가장 큰 번호에 1을 더한다(A-92). 번호가 겹치지 않으므로 문제가 없다.
4. **로그인의 부수 효과**: 로그인한 관리자의 비밀번호가 argon2id로 바뀌고 `lang`이 접속 언어로 바뀐다(v1.6도 로그인할 때 `user.lang`을 갱신한다, `UserApp.java:317`). 다른 사용자는 그대로이다.

가져온 v2의 화면은 이슈 목록, 이슈 상세, 게시글, 타임라인, 사용자 첫 화면이 200이고 이슈 목록에 댓글 수가 보인다. **저장소를 읽는 화면(`code`, `commits`, `settingform`, `newPullRequestForm`)은 500이다.** 가져오기가 Git 저장소를 옮기지 않아 `data/repo/git/<소유자>/<프로젝트>.git`이 없기 때문이다(서버 로그의 `repository not found`). 가져오기와 함께 `yona.data-dir`의 저장소 디렉터리를 같이 옮겨야 한다.

2026-10-28의 화면 비교 보고서(`yona-diff-v2/report-xport1-pages1.md`: 같음 1,035, 다름 186, 가져오기 없는 기준 102)는 이전 빌드 기준이다. 새로 생긴 차이 119건 가운데 34건(v2가 500)은 위 저장소 부재이다. 나머지(댓글 수 표시 26건, 페이지 없음 4건, 기타 55건)는 그 보고서만으로는 원인을 가를 수 없고, 현재 빌드에서 댓글 수는 보인다. 현재 빌드로 같은 비교를 다시 하려면 저장소를 포함한 데이터 디렉터리를 같이 옮긴 상태에서 `tools/v16-diff/xport1.sh`를 돌려야 한다.

