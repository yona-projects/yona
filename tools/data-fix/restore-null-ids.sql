-- R-56-6 교정 스크립트: 데이터 가져오기에서 NULL이 0으로 바뀐 외래 키 컬럼을 NULL로 되돌린다.
--
-- 배경: 2026-11-05 이전의 데이터 가져오기(v1.6과 같은 동작)는 NULL을 허용하는 정수 컬럼 가운데 교환기가 NULL 허용으로 표시하지 않은 컬럼에서
-- JSON null을 0으로 넣었다. 번호(`*_id`) 컬럼의 0은 존재하지 않는 행을 가리키는 값이므로 NULL로 되돌려도 안전하다.
-- 이 수정 이후의 가져오기는 DB가 NULL을 허용하는 컬럼이면 JSON null을 NULL로 넣는다.
--
-- 사용법 (반드시 백업한 뒤, 먼저 1단계의 건수를 확인한다):
--   1단계: 아래 SELECT를 실행해 컬럼마다 0인 행의 수를 본다. 0건이면 해당 컬럼은 손댈 것이 없다.
--   2단계: 건수가 의도와 맞으면 UPDATE 구문의 주석을 풀어 실행한다.
--
-- 아래 컬럼은 정수이지만 번호가 아니라서 0이 정상 값일 수 있으므로 이 스크립트가 고치지 않는다. 필요하면 운영자가 직접 확인한다:
--   attachment.size, issue.num_of_comments, issue.state, issue.number, milestone.state, oidc_sso_settings.clock_skew_seconds, posting.num_of_comments, posting.number, project.last_issue_number, project.last_posting_number, project.default_reviewer_count, pull_request.state, pull_request.number, ruleset.required_approvals, saml2_sso_settings.clock_skew_seconds
--
-- 표준 SQL이라 PostgreSQL, MariaDB, MySQL, H2, MSSQL에서 그대로 실행된다.

-- 1단계: 컬럼마다 0인 행의 수
select 'api_token.user_id' as target, count(*) as zero_rows from api_token where user_id = 0
union all
select 'assignee.user_id' as target, count(*) as zero_rows from assignee where user_id = 0
union all
select 'assignee.project_id' as target, count(*) as zero_rows from assignee where project_id = 0
union all
select 'comment_thread.project_id' as target, count(*) as zero_rows from comment_thread where project_id = 0
union all
select 'deploy_key.project_id' as target, count(*) as zero_rows from deploy_key where project_id = 0
union all
select 'email.user_id' as target, count(*) as zero_rows from email where user_id = 0
union all
select 'gpg_key.user_id' as target, count(*) as zero_rows from gpg_key where user_id = 0
union all
select 'issue.author_id' as target, count(*) as zero_rows from issue where author_id = 0
union all
select 'issue.project_id' as target, count(*) as zero_rows from issue where project_id = 0
union all
select 'issue_comment.author_id' as target, count(*) as zero_rows from issue_comment where author_id = 0
union all
select 'issue_comment.issue_id' as target, count(*) as zero_rows from issue_comment where issue_id = 0
union all
select 'issue_label.category_id' as target, count(*) as zero_rows from issue_label where category_id = 0
union all
select 'issue_label_category.project_id' as target, count(*) as zero_rows from issue_label_category where project_id = 0
union all
select 'known_device.user_id' as target, count(*) as zero_rows from known_device where user_id = 0
union all
select 'lfs_lock.project_id' as target, count(*) as zero_rows from lfs_lock where project_id = 0
union all
select 'lfs_lock.owner_user_id' as target, count(*) as zero_rows from lfs_lock where owner_user_id = 0
union all
select 'oauth_authorization.client_row_id' as target, count(*) as zero_rows from oauth_authorization where client_row_id = 0
union all
select 'oauth_authorization.user_id' as target, count(*) as zero_rows from oauth_authorization where user_id = 0
union all
select 'oauth_client.owner_id' as target, count(*) as zero_rows from oauth_client where owner_id = 0
union all
select 'oauth_consent.client_row_id' as target, count(*) as zero_rows from oauth_consent where client_row_id = 0
union all
select 'oauth_consent.user_id' as target, count(*) as zero_rows from oauth_consent where user_id = 0
union all
select 'organization_oauth_approval.organization_id' as target, count(*) as zero_rows from organization_oauth_approval where organization_id = 0
union all
select 'organization_oauth_approval.client_row_id' as target, count(*) as zero_rows from organization_oauth_approval where client_row_id = 0
union all
select 'organization_user.user_id' as target, count(*) as zero_rows from organization_user where user_id = 0
union all
select 'organization_user.organization_id' as target, count(*) as zero_rows from organization_user where organization_id = 0
union all
select 'organization_user.role_id' as target, count(*) as zero_rows from organization_user where role_id = 0
union all
select 'posting.author_id' as target, count(*) as zero_rows from posting where author_id = 0
union all
select 'posting.project_id' as target, count(*) as zero_rows from posting where project_id = 0
union all
select 'project_menu_setting.project_id' as target, count(*) as zero_rows from project_menu_setting where project_id = 0
union all
select 'project_pushed_branch.project_id' as target, count(*) as zero_rows from project_pushed_branch where project_id = 0
union all
select 'project_transfer.project_id' as target, count(*) as zero_rows from project_transfer where project_id = 0
union all
select 'project_user.user_id' as target, count(*) as zero_rows from project_user where user_id = 0
union all
select 'project_user.project_id' as target, count(*) as zero_rows from project_user where project_id = 0
union all
select 'project_user.role_id' as target, count(*) as zero_rows from project_user where role_id = 0
union all
select 'project_visitation.project_id' as target, count(*) as zero_rows from project_visitation where project_id = 0
union all
select 'project_visitation.recently_visited_projects_id' as target, count(*) as zero_rows from project_visitation where recently_visited_projects_id = 0
union all
select 'pull_request.to_project_id' as target, count(*) as zero_rows from pull_request where to_project_id = 0
union all
select 'pull_request.from_project_id' as target, count(*) as zero_rows from pull_request where from_project_id = 0
union all
select 'pull_request_commit.pull_request_id' as target, count(*) as zero_rows from pull_request_commit where pull_request_id = 0
union all
select 'pull_request_event.pull_request_id' as target, count(*) as zero_rows from pull_request_event where pull_request_id = 0
union all
select 'pull_request_review.pull_request_id' as target, count(*) as zero_rows from pull_request_review where pull_request_id = 0
union all
select 'pull_request_review.author_id' as target, count(*) as zero_rows from pull_request_review where author_id = 0
union all
select 'recently_visited_projects.user_id' as target, count(*) as zero_rows from recently_visited_projects where user_id = 0
union all
select 'ruleset.project_id' as target, count(*) as zero_rows from ruleset where project_id = 0
union all
select 'ssh_key.user_id' as target, count(*) as zero_rows from ssh_key where user_id = 0
union all
select 'two_factor_backup_code.user_id' as target, count(*) as zero_rows from two_factor_backup_code where user_id = 0
union all
select 'two_factor_credential.user_id' as target, count(*) as zero_rows from two_factor_credential where user_id = 0
union all
select 'user_project_notification.user_id' as target, count(*) as zero_rows from user_project_notification where user_id = 0
union all
select 'user_project_notification.project_id' as target, count(*) as zero_rows from user_project_notification where project_id = 0
order by target;

-- 2단계: 0을 NULL로 되돌린다 (확인한 뒤 주석을 푼다)
-- update api_token set user_id = null where user_id = 0;
-- update assignee set user_id = null where user_id = 0;
-- update assignee set project_id = null where project_id = 0;
-- update comment_thread set project_id = null where project_id = 0;
-- update deploy_key set project_id = null where project_id = 0;
-- update email set user_id = null where user_id = 0;
-- update gpg_key set user_id = null where user_id = 0;
-- update issue set author_id = null where author_id = 0;
-- update issue set project_id = null where project_id = 0;
-- update issue_comment set author_id = null where author_id = 0;
-- update issue_comment set issue_id = null where issue_id = 0;
-- update issue_label set category_id = null where category_id = 0;
-- update issue_label_category set project_id = null where project_id = 0;
-- update known_device set user_id = null where user_id = 0;
-- update lfs_lock set project_id = null where project_id = 0;
-- update lfs_lock set owner_user_id = null where owner_user_id = 0;
-- update oauth_authorization set client_row_id = null where client_row_id = 0;
-- update oauth_authorization set user_id = null where user_id = 0;
-- update oauth_client set owner_id = null where owner_id = 0;
-- update oauth_consent set client_row_id = null where client_row_id = 0;
-- update oauth_consent set user_id = null where user_id = 0;
-- update organization_oauth_approval set organization_id = null where organization_id = 0;
-- update organization_oauth_approval set client_row_id = null where client_row_id = 0;
-- update organization_user set user_id = null where user_id = 0;
-- update organization_user set organization_id = null where organization_id = 0;
-- update organization_user set role_id = null where role_id = 0;
-- update posting set author_id = null where author_id = 0;
-- update posting set project_id = null where project_id = 0;
-- update project_menu_setting set project_id = null where project_id = 0;
-- update project_pushed_branch set project_id = null where project_id = 0;
-- update project_transfer set project_id = null where project_id = 0;
-- update project_user set user_id = null where user_id = 0;
-- update project_user set project_id = null where project_id = 0;
-- update project_user set role_id = null where role_id = 0;
-- update project_visitation set project_id = null where project_id = 0;
-- update project_visitation set recently_visited_projects_id = null where recently_visited_projects_id = 0;
-- update pull_request set to_project_id = null where to_project_id = 0;
-- update pull_request set from_project_id = null where from_project_id = 0;
-- update pull_request_commit set pull_request_id = null where pull_request_id = 0;
-- update pull_request_event set pull_request_id = null where pull_request_id = 0;
-- update pull_request_review set pull_request_id = null where pull_request_id = 0;
-- update pull_request_review set author_id = null where author_id = 0;
-- update recently_visited_projects set user_id = null where user_id = 0;
-- update ruleset set project_id = null where project_id = 0;
-- update ssh_key set user_id = null where user_id = 0;
-- update two_factor_backup_code set user_id = null where user_id = 0;
-- update two_factor_credential set user_id = null where user_id = 0;
-- update user_project_notification set user_id = null where user_id = 0;
-- update user_project_notification set project_id = null where project_id = 0;
