# 데이터 교정 스크립트

운영 중인 데이터를 교정하는 SQL 스크립트이다. 모두 표준 SQL이라 PostgreSQL, MariaDB, MySQL, H2, MSSQL에서 실행된다. **먼저 백업하고**, 각 파일의 1단계(건수 확인)를 본 뒤 2단계의 UPDATE를 실행한다. UPDATE는 주석으로 막혀 있다.

| 파일 | 대상 | 설명 |
|---|---|---|
| `restore-null-ids.sql` | R-56-6 | 옛 가져오기가 NULL을 0으로 바꾼 외래 키 컬럼을 NULL로 되돌린다 |
| `normalize-prev-commit-id.sql` | R-44-3 | 코드 리뷰 스레드의 `prev_commit_id`(NULL과 빈 문자열)를 한 가지 표현으로 통일한다 |

자세한 배경은 [yona-v2-wiki/architecture/data-fix-and-known-limits.md](../../yona-v2-wiki/architecture/data-fix-and-known-limits.md)에 있다.
