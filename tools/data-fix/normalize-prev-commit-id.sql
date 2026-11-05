-- R-44-3 보완 스크립트: 코드 리뷰 스레드의 `prev_commit_id` 표현을 하나로 통일한다.
--
-- 배경: 커밋 화면에서 단 코드 리뷰 스레드는 `prev_commit_id`를 NULL로 저장하고, 풀 리퀘스트의 커밋 하나에 단 스레드는 빈 문자열('')로
-- 저장한다. 두 값은 "비교할 이전 커밋이 없다"는 같은 뜻이다(v1.6에서 온 동작이며 의도적으로 그대로 둔다).
-- v2의 화면 코드(ReviewLinks, PullRequestChangesService)는 둘 다 "비어 있음"으로 다루므로 화면에는 영향이 없다.
-- 다만 알림 메일의 차이 조각(NotificationMessages.commentedCode)은 NULL이면 커밋의 변경을, 빈 문자열이 아니면 두 커밋 사이의 변경을 만든다.
--
-- 사용법 (백업한 뒤): 먼저 1단계로 건수를 확인하고, 통일할 방향의 UPDATE 하나만 주석을 풀어 실행한다.
--   - 빈 문자열을 NULL로 통일(권장: 코드의 "이전 커밋 없음" 판정이 NULL 기준이다).
--   - NULL을 빈 문자열로 통일(v1.6 `CodeCommentThread.isCommitComment`가 빈 문자열을 기준으로 삼았다).
--
-- 표준 SQL이라 PostgreSQL, MariaDB, MySQL, H2, MSSQL에서 그대로 실행된다.

-- 1단계: 표현별 건수
select dtype,
       sum(case when prev_commit_id is null then 1 else 0 end) as null_rows,
       sum(case when prev_commit_id = '' then 1 else 0 end) as empty_rows,
       sum(case when prev_commit_id <> '' then 1 else 0 end) as with_previous_commit
  from comment_thread
 where dtype in ('ranged', 'non_ranged')
 group by dtype;

-- 2단계 (하나만 고른다)
-- update comment_thread set prev_commit_id = null where prev_commit_id = '' and dtype in ('ranged', 'non_ranged');
-- update comment_thread set prev_commit_id = '' where prev_commit_id is null and dtype in ('ranged', 'non_ranged');
