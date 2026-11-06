# 데이터 교정 스크립트와 알려진 한계

v1.6에서 온 동작을 의도적으로 그대로 두었거나 고친 결과, 운영 데이터에 남을 수 있는 흔적과 그 교정 방법, 그리고 고치지 않기로 정한 한계를 적는다. 판단은 [decisions.md](../decisions.md)의 A-372에 있다.

교정 스크립트는 저장소의 `tools/data-fix/`에 있다. 표준 SQL이며 PostgreSQL, MariaDB, MySQL, H2, MSSQL에서 실행된다. 먼저 백업하고 1단계(건수 확인)를 본 뒤 2단계의 UPDATE를 실행한다.

## R-56-6: 가져오기에서 NULL이 0이 된 컬럼

- **원인:** 데이터 가져오기(`DataService.bind`)는 NULL을 허용하는 정수 컬럼 가운데 교환기가 NULL 허용으로 표시한(`nullableLong`) 컬럼만 JSON null을 NULL로 넣었다. 표시가 빠진 컬럼은 0이 되었고, 시각은 JSON이 숫자가 아니거나 0이면 NULL이 되었다(에폭 0의 시각도 NULL).
- **수정(A-372):** DB 메타데이터가 NULL을 허용하는 정수 컬럼이면 JSON null을 NULL로 넣고, 시각은 JSON null(숫자가 아닌 값)일 때만 NULL로 넣는다. 에폭 0은 그대로 가져온다.
- **교정:** 이 수정 전에 가져온 데이터는 NULL이어야 할 외래 키가 0일 수 있다. [`tools/data-fix/restore-null-ids.sql`](../../tools/data-fix/restore-null-ids.sql)이 `*_id` 컬럼의 0을 NULL로 되돌린다. 번호가 아닌 정수 컬럼(`num_of_comments`, `number`, `state` 등)은 0이 정상 값일 수 있어 스크립트가 고치지 않고 목록만 적어 둔다.
- **시각:** 옛 가져오기가 에폭 0 시각을 NULL로 바꾼 것은 되돌릴 방법이 없다(원래 NULL이었는지 알 수 없다). 에폭 0(1970-01-01)의 시각은 실제 데이터에서 나오지 않는다.

## R-44-3: 코드 리뷰 스레드의 `prev_commit_id`

- **동작:** 커밋 화면에서 단 스레드는 `prev_commit_id`가 NULL이고, 풀 리퀘스트의 커밋 하나에 단 스레드는 빈 문자열이다. 둘 다 "비교할 이전 커밋이 없다"는 뜻이다(v1.6 그대로).
- **결정:** 유지한다(A-372). v2의 화면 코드는 둘 다 "비어 있음"으로 다룬다.
- **알려진 한계:** 알림 메일의 차이 조각은 NULL이면 커밋의 변경을, 빈 문자열이 아니면 두 커밋 사이의 변경을 만든다. 풀 리퀘스트의 커밋 하나에 단 스레드(빈 문자열)는 두 커밋 사이의 변경을 만들지 못해 알림에 댓글만 나올 수 있다.
- **보완:** 표현을 하나로 통일하려면 [`tools/data-fix/normalize-prev-commit-id.sql`](../../tools/data-fix/normalize-prev-commit-id.sql)을 쓴다. 코드는 두 표현을 모두 읽으므로 스크립트를 쓰지 않아도 동작한다.

## R-54-6: 대량 메일의 `mailto:` 길이 한도

- **동작:** 사이트 관리자의 대량 메일 화면은 받는 사람 주소를 `mailto:` 하나로 연다(v1.6 그대로).
- **결정:** 문서화만 한다(A-372). 브라우저와 메일 프로그램마다 `mailto:`의 길이 한도가 다르다(보통 수천 자). 받는 사람이 아주 많으면 메일 프로그램이 받지 못할 수 있다.
- **우회:** 프로젝트를 나누어 선택하거나 받는 사람을 숨은참조로 붙여 넣는다. 실제로 이런 규모는 드물다고 보아 고치지 않는다.

## R-56-7: 데이터 내보내기와 가져오기는 DB 정보만 다룬다

- **동작:** `/sites/export`와 `/sites/import`는 DB의 테이블만 다룬다. 첨부 파일의 본문(업로드 디렉터리), 저장소(`repo/git`, `repo/svn`, 위키 저장소), 설정 파일(`config/`)은 들어 있지 않다(v1.6 그대로).
- **결정:** 결함이 아니다(A-374). 파일들은 원래 개별 백업 대상이다. 데이터 화면에 이 안내(`site.data.warning4`)를 더했다.
- **옮길 때:** DB 파일과 함께 데이터 디렉터리(`yona.data-dir`) 전체를 따로 복사한다.

## R-56-2: 내보내기와 가져오기에서 빠진 테이블과 컬럼 (2026-11-05 확인)

현재 스키마(Hibernate가 만드는 테이블)와 `DataTables`(내보내기와 가져오기 표, 69개)를 비교한 결과이다.

**표에 없는 테이블 17개**

| 구분 | 테이블 | 내용 |
|---|---|---|
| 사용자 편의 | `favorite_project`, `favorite_issue`, `favorite_organization` | 즐겨찾기 |
| | `recent_project`, `recent_issue` | 최근 방문한 프로젝트와 최근 본 이슈 |
| | `user_setting` | 로그인 후 첫 화면 설정 |
| 이슈와 글 | `issue_sharer` | 이슈 공유자 |
| | `title_head` | 제목 머리말 빈도 |
| | `posting_issue_label` | 게시글에 붙은 라벨 |
| 연동 | `webhook`, `webhook_thread` | 웹훅과 웹훅 스레드(Hangouts Chat 등) |
| 계정 | `linked_account`, `user_credential` | 소셜 로그인 연결, 사용자 자격(소셜 가입) |
| | `user_verification` | 이메일 인증 코드 |
| | `device_authorization` | 기기 인증(CLI 로그인) 진행 상태 |
| 감사 | `audit_log`, `audit_chain_head`, `audit_stream_cursor` | 감사 로그와 해시 체인, 외부 전송 위치 |

**표에는 있지만 컬럼이 빠진 테이블**

| 테이블 | 빠진 컬럼 |
|---|---|
| `n4user` | `is_guest`, `english_name`, `token` (R-56-1에서 더했다) |
| `issue` | `is_draft`, `weight`, `parent_id`, `updated_by_author_id`, `history` (R-56-1에서 더했다) |
| `issue_comment` | `parent_comment_id` (댓글에 단 답글의 부모) |
| `posting` | `parent_id`, `updated_by_author_id`, `history` |
| `posting_comment` | `parent_comment_id`, `project_id` |
| `project` | `is_code_accessible_member_only`, `previous_name`, `previous_owner_login_id`, `previous_name_changed_time` (코드 공개 범위, 이전 이름 기록) |
| `attachment` | `owner_login_id` |
| `review_comment` | `edited_date` (고친 시각) |
| `comment_thread`, `review_comment` | `pending_date` (임시 저장 리뷰라 일부러 내보내지 않는다, X-26) |

위 두 표가 R-56-2의 실제 범위이다. 즐겨찾기와 웹훅 등을 넣기로 하셨고, 어느 테이블을 넣을지는 선생님 확인을 기다린다.
