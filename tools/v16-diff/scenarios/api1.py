"""`/-_-api/v1` 토큰 API를 읽기, 쓰기, 오류 순서로 호출한다. 토큰은 `users/token` 응답에서 읽어 `Authorization: token <값>`으로 보낸다.
data2 상태에서 한 번만 실행한다(이슈, 글, 마일스톤, 프로젝트를 만든다). 쿠키 로그인과 섞이지 않도록 토큰 전용 행위자(`api`)를 쓴다."""
from harness import Step
from data1 import login

NORMALIZE_ENTITY_IDS = True
TOK = {"Authorization": "token <<tok>>"}
OWNER = "/-_-api/v1/owners/tester/projects/proj1"


def api(method, path, body=None, note="", actor="api", headers=None, **kw):
    return Step(actor, method, "/-_-api/v1" + path if path.startswith("/") and not path.startswith("/-_-api") else path,
                json_body=body, headers=TOK if headers is None else headers, note=note or f"{method} {path}", **kw)


def owner(method, tail, body=None, note="", **kw):
    return Step(kw.pop("actor", "api"), method, OWNER + tail, json_body=body, headers=kw.pop("headers", TOK), note=note or f"{method} {tail}", **kw)


def steps():
    s = []
    s += [login("tester"), login("other"), login("admin", "admin1234")]
    # 토큰 받기
    s += [
        Step("anon", "GET", "/-_-api/v1/hello", note="hello 익명"),
        Step("anon", "GET", "/-_-api/v1/", note="index 익명"),
        Step("maker", "POST", "/-_-api/v1/users/token", note="토큰 본문 없음"),
        Step("maker", "POST", "/-_-api/v1/users/token", json_body={"id": "nobody", "password": "x"}, note="토큰 없는 사용자"),
        Step("maker", "POST", "/-_-api/v1/users/token", json_body={"id": "tester", "password": "틀림"}, note="토큰 틀린 암호"),
        Step("maker", "POST", "/-_-api/v1/users/token", data={"id": "tester", "password": "pass1234"}, note="토큰 폼 본문"),
        Step("maker", "POST", "/-_-api/v1/users/token", json_body={"id": "tester@example.com", "password": "pass1234"}, note="토큰 이메일로", capture={"tok": ("body", r'"access_token":"([^"]+)"')}),
        Step("maker", "POST", "/-_-api/v1/users/token", json_body={"id": "other", "password": "pass1234"}, note="토큰 other", capture={"tok_other": ("body", r'"access_token":"([^"]+)"')}),
    ]
    # 읽기
    s += [
        api("GET", "/hello", note="hello 토큰"),
        api("GET", "/hello", headers={}, note="hello 토큰 없음"),
        api("GET", "/hello", headers={"Authorization": "token invalidtoken"}, note="hello 잘못된 토큰"),
        api("GET", "/hello", headers={"Authorization": "Bearer <<tok>>"}, note="hello Bearer"),
        api("GET", "/users", note="사용자 검색"),
        api("GET", "/users?query=oth", note="사용자 검색 질의"),
        api("GET", "/users/tester/statistics", note="통계"),
        api("GET", "/users/nobody/statistics", note="통계 없는 사용자"),
        api("GET", "/users/tester/statistics", headers={}, note="통계 익명"),
        api("GET", "/user/issues", note="내 이슈"),
        api("GET", "/user/issues?filter=created&page=1&pageNum=5", note="내 이슈 만든"),
        api("GET", "/user/issues?filter=bogus", note="내 이슈 잘못된 필터"),
        api("GET", "/user/issues", headers={}, note="내 이슈 익명"),
        api("GET", "/favoriteProjects", note="즐겨찾기 프로젝트"),
        api("GET", "/favoriteOrganizations", note="즐겨찾기 조직"),
        api("GET", "/favoriteIssues", note="즐겨찾기 이슈"),
        owner("GET", "/issues/1", note="이슈 가져오기"),
        owner("GET", "/issues/99", note="이슈 없음"),
        owner("GET", "/exports", note="내보내기"),
        owner("GET", "/titleHeads", note="머리말"),
        owner("GET", "/titleHeads?query=버", note="머리말 질의"),
        owner("GET", "/assignableUsers", note="담당 가능"),
        owner("GET", "/assignableUsers?query=o", note="담당 가능 질의"),
        owner("GET", "/issues/1/assignableUsers", note="이슈 담당 가능"),
        owner("GET", "/issues/1/findSharer?query=o", note="공유 찾기"),
        owner("GET", "/issues/1/sharableUsers?query=o", note="공유 가능"),
        owner("GET", "/posts/2/watchers", note="글 구독자"),
        owner("GET", "/exports", headers={}, note="내보내기 익명"),
    ]
    # 즐겨찾기 토글
    s += [
        api("POST", "/favoriteProjects/1", note="즐겨찾기 프로젝트 켜기"),
        api("GET", "/favoriteProjects", note="즐겨찾기 프로젝트 확인"),
        api("POST", "/favoriteProjects/1", note="즐겨찾기 프로젝트 끄기"),
        api("POST", "/favoriteProjects/abc", note="즐겨찾기 프로젝트 잘못된 번호"),
        api("POST", "/favoriteIssues/1", note="즐겨찾기 이슈 켜기"),
        api("GET", "/favoriteIssues", note="즐겨찾기 이슈 확인"),
        api("POST", "/favoriteOrganizations/1", note="즐겨찾기 조직 켜기"),
        api("POST", "/favoriteProjects/1", headers={}, note="즐겨찾기 익명"),
    ]
    # 쓰기: 이슈
    s += [
        owner("POST", "/labels", {"labels": [{"labelName": "api", "labelColor": "#112233", "category": "Api", "isExclusive": False}]}, note="라벨 만들기"),
        owner("POST", "/issues", {"issues": [{"title": "API 이슈", "body": "API로 만듦", "author": {"loginId": "tester", "name": "Tester", "email": "tester@example.com"}, "createdAt": "2026-01-01 AM 12:00:00 +0000",
                                              "updatedAt": "2026-01-01 AM 12:00:00 +0000", "assignees": [{"loginId": "other", "email": "other@example.com"}], "temporaryUploadFiles": []}], "sendNotification": False}, note="이슈 만들기"),
        owner("POST", "/issues", {"issues": []}, note="이슈 만들기 빈 목록"),
        owner("POST", "/issues", {"nothing": 1}, note="이슈 만들기 필드 없음"),
        owner("POST", "/issues", {"issues": [{"title": "익명"}]}, headers={}, note="이슈 만들기 익명"),
        owner("POST", "/issues/imports?postNumber=1", note="글을 이슈로 가져오기"),
        owner("POST", "/issues/imports", note="글을 이슈로 가져오기 번호 없음"),
        owner("PUT", "/issues/1", {"title": "수정된 제목", "body": "수정된 본문", "milestoneTitle": "마일스톤1", "state": "OPEN", "assignees": [{"loginId": "other", "email": "other@example.com"}]}, note="이슈 수정"),
        owner("PUT", "/issues/99", {"title": "x", "body": "y", "state": "OPEN", "assignees": []}, note="이슈 수정 없음"),
        owner("PATCH", "/issues/1", {"state": "CLOSED"}, note="이슈 닫기"),
        owner("PATCH", "/issues/1", {"state": "OPEN"}, note="이슈 열기"),
        owner("PATCH", "/issues/1", {"state": "bogus"}, note="이슈 잘못된 상태"),
        owner("PATCH", "/issues/1/content", {"content": "새 본문", "original": "내용입니다 @other 확인 부탁"}, note="이슈 본문 고치기"),
        owner("PATCH", "/issues/1/content", {"content": "충돌", "original": "다른 원본"}, note="이슈 본문 충돌"),
        owner("POST", "/issues/1/comments", {"comment": "API 댓글", "parentCommentId": ""}, note="이슈 댓글"),
        owner("POST", "/issues/1/comments", {"comment": "API 댓글 답", "parentCommentId": "1"}, note="이슈 댓글 답"),
        owner("POST", "/issues/1/comments", {"comment": ""}, note="이슈 댓글 빈 값"),
        owner("POST", "/issues/1/commentNotiReceivers", {"comment": "@other 확인", "parentCommentId": ""}, note="댓글 알림 대상"),
        owner("POST", "/issues/1/assignees", {"assignees": [{"loginId": "other", "email": "other@example.com"}]}, note="담당자"),
        owner("POST", "/issues/1/share", {"sharers": [{"loginId": "other"}]}, note="공유"),
        owner("POST", "/issues/1/upvoteWeight", note="가중치 올리기"),
        owner("POST", "/issues/1/downvoteWeight", note="가중치 내리기"),
        owner("POST", "/issues/1/detectChange", {"issueBodyChecksum": "x", "numOfComments": 0}, note="변경 감지"),
        owner("POST", "/issuelabel/1", [1], note="이슈 라벨"),
        owner("POST", "/translation", note="번역", actor="api") if False else Step("api", "POST", "/-_-api/v1/translation", json_body={"text": "hello", "target": "ko"}, headers=TOK, note="번역"),
    ]
    # 쓰기: 글, 마일스톤, 라벨, 프로젝트, 사용자
    s += [
        owner("POST", "/posts", {"posts": [{"title": "API 글", "body": "본문", "author": {"loginId": "tester", "name": "Tester", "email": "tester@example.com"}, "createdAt": "2026-01-01 AM 12:00:00 +0000", "updatedAt": "2026-01-01 AM 12:00:00 +0000", "temporaryUploadFiles": []}]}, note="글 만들기"),
        owner("PATCH", "/posts/2/content", {"content": "새 글 본문", "original": "게시글 본문 @other"}, note="글 본문 고치기"),
        owner("POST", "/posts/2/comments", {"author": {"loginId": "other", "name": "Other", "email": "other@example.com"}, "body": "API 글 댓글", "createdAt": "2026-01-01 AM 12:00:00 +0000", "temporaryUploadFiles": []}, note="글 댓글"),
        owner("POST", "/postlabel/2", [1], note="글 라벨"),
        owner("POST", "/milestones", {"milestones": [{"title": "API 마일스톤", "description": "설명", "state": "open", "due_on": "2030-05-05T00:00:00Z"}]}, note="마일스톤 만들기"),
        owner("POST", "/milestones", {"milestones": [{"title": "API 마일스톤", "description": "중복", "state": "open", "due_on": "2030-05-05T00:00:00Z"}]}, note="마일스톤 중복"),
        Step("api", "POST", "/-_-api/v1/owners/tester/projects", json_body={"projectName": "apiproj", "projectScope": "PUBLIC", "projectDescription": "API로 만듦", "projectVcs": "GIT",
                                                                              "projectCreatedDate": "2026-01-01 AM 12:00:00 +0000", "members": [{"email": "other@example.com", "role": "member"}]},
             headers=TOK, note="프로젝트 만들기"),
        Step("api", "POST", "/-_-api/v1/owners/tester/projects", json_body={"projectName": "apiproj", "projectScope": "PUBLIC"}, headers=TOK, note="프로젝트 중복"),
        Step("api", "POST", "/-_-api/v1/owners/other/projects", json_body={"projectName": "남의것", "projectScope": "PUBLIC"}, headers=TOK, note="프로젝트 남의 소유자"),
        Step("api", "POST", "/-_-api/v1/users", json_body={"users": [{"loginId": "apiuser", "name": "API 사용자", "email": "apiuser@example.com"}]}, headers=TOK, note="사용자 만들기 토큰"),
        Step("anon", "POST", "/-_-api/v1/users", json_body={"users": [{"loginId": "apiuser2", "name": "API 사용자2", "email": "apiuser2@example.com"}]}, note="사용자 만들기 익명"),
        Step("anon", "GET", "/-_-api/v1/admin/users", note="관리자 사용자 익명"),
        Step("api", "GET", "/-_-api/v1/admin/users", headers=TOK, note="관리자 사용자 일반 토큰"),
        Step("anon", "PATCH", "/-_-api/v1/admin/users/apiuser", json_body={"state": "LOCKED"}, note="관리자 상태 익명"),
        Step("anon", "POST", "/-_-api/v1/user/defultLoginPage", data={"defaultLoginPage": "ISSUE"}, note="기본 로그인 화면 익명"),
        Step("tester", "POST", "/-_-api/v1/user/defultLoginPage", data={"defaultLoginPage": "ISSUE"}, note="기본 로그인 화면"),
    ]
    # 쓰기 결과 읽기
    s += [
        owner("GET", "/issues/1", note="수정 뒤 이슈"),
        Step("anon", "GET", "/tester/proj1/issues", note="이슈 목록 뒤"),
        Step("anon", "GET", "/tester/proj1/issue/1", note="이슈 1 화면"),
        Step("anon", "GET", "/tester/proj1/milestones", note="마일스톤 목록 뒤"),
        Step("anon", "GET", "/tester/proj1/posts", note="글 목록 뒤"),
        Step("anon", "GET", "/tester/apiproj", note="API 프로젝트"),
        Step("admin", "GET", "/-_-api/v1/admin/users", note="관리자 사용자"),
        Step("admin", "PATCH", "/-_-api/v1/admin/users/apiuser", json_body={"state": "LOCKED"}, note="관리자 상태 바꾸기"),
        Step("admin", "PATCH", "/-_-api/v1/admin/users/nobody", json_body={"state": "LOCKED"}, note="관리자 상태 없는 사용자"),
        Step("admin", "GET", "/-_-api/v1/admin/users", note="관리자 사용자 뒤"),
    ]
    return s
