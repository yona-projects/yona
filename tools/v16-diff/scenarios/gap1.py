"""라우트 대조에서 어떤 시나리오도 요청하지 않았던 라우트(`coverage.py`가 찾은 것)를 모든 행위자로 부른다: 이전 데이터 내보내기(`/migration/*`),
`POST /`, 비밀번호 직접 바꾸기, API로 이슈 댓글과 글 댓글 고치기, 푸시한 브랜치 지우기, 분류 하나 읽기, 풀 리퀘스트의 커밋별 변경, 코드 보기의 메타 정보(`code/!/`),
커밋 코멘트를 옛 주소로 지우기, 브랜치 지우기, svn HEAD와 POST.
pr1a와 pr1.sh를 먼저 돌린 data3 상태에서 실행한다."""
import subprocess

from harness import Step
from data1 import login, mp

B = "/tester/proj1"
ACTORS = ["anon", "tester", "other", "third", "admin"]
TOK = {"Authorization": "token <<tok>>"}
TOK_OTHER = {"Authorization": "token <<tok_other>>"}
H = subprocess.run(["git", "-C", "/home/jiho/yona-convert/yona-diff-work/git-tester-proj1-base", "log", "--reverse", "--format=%H"], capture_output=True, text=True, check=True).stdout.split()


def steps():
    s = []
    s += [login("tester"), login("other"), login("third"), login("admin", "admin1234")]
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    s += [
        Step("maker", "POST", "/-_-api/v1/users/token", json_body={"id": "tester", "password": "pass1234"}, capture={"tok": ("body", r'"access_token":"([^"]+)"')}, note="토큰 tester"),
        Step("maker", "POST", "/-_-api/v1/users/token", json_body={"id": "other", "password": "pass1234"}, capture={"tok_other": ("body", r'"access_token":"([^"]+)"')}, note="토큰 other"),
    ]
    # POST /
    s += [Step(a, "POST", "/", note=f"POST / [{a}]") for a in ("anon", "tester")]
    # 이전 데이터 내보내기
    paths = ["/migration/projects"]
    for pr in ("proj1", "priv1", "prot1", "nope"):
        paths += [f"/migration/tester/projects/{pr}"] + [f"/migration/tester/projects/{pr}/{x}" for x in ("labels", "issuelabel", "milestones", "issues", "posts")]
    for p in paths:
        s += [Step(a, "GET", p, note=f"{p} [{a}]") for a in ACTORS]
    # 비밀번호 직접 바꾸기 (third로)
    s += [
        Step("anon", "POST", "/user/resetPassword", data={"loginId": "third", "oldPassword": "pass1234", "password": "new12345", "retypedPassword": "new12345"}, note="비밀번호 익명"),
        Step("third", "POST", "/user/resetPassword", data={"loginId": "third"}, note="비밀번호 빈 값"),
        Step("third", "POST", "/user/resetPassword", data={"loginId": "third", "oldPassword": "틀림", "password": "new12345", "retypedPassword": "new12345"}, note="비밀번호 현재 틀림"),
        Step("third", "POST", "/user/resetPassword", data={"loginId": "third", "oldPassword": "pass1234", "password": "new12345", "retypedPassword": "다름"}, note="비밀번호 확인 다름"),
        Step("third", "POST", "/user/resetPassword", data={"loginId": "third", "oldPassword": "pass1234", "password": "", "retypedPassword": ""}, note="비밀번호 새 값 없음"),
        Step("third", "POST", "/user/resetPassword", data={"loginId": "third", "oldPassword": "pass1234", "password": "new12345", "retypedPassword": "new12345"}, note="비밀번호 바꾸기"),
        Step("third", "GET", "/user/editform", note="바꾼 뒤 편집 화면(로그아웃)"),
        Step("third", "POST", "/users/login", data={"loginIdOrEmail": "third", "password": "pass1234"}, note="옛 비밀번호 로그인"),
        Step("third", "POST", "/users/login", data={"loginIdOrEmail": "third", "password": "new12345"}, note="새 비밀번호 로그인"),
    ]
    # 이슈 댓글과 글 댓글을 API로 고치기
    s += [
        Step("tester", "POST", f"{B}/issue/1/comments", files=mp(contents="고칠 댓글"), capture={"cid": ("location", r"#comment-(\d+)")}, note="고칠 이슈 댓글"),
        Step("tester", "POST", f"{B}/post/1/comment", files=mp(contents="고칠 글 댓글"), capture={"pcid": ("location", r"#comment-(\d+)")}, note="고칠 글 댓글"),
    ]
    for label, method, path, body in (
        ("이슈 댓글 PUT", "PUT", "/-_-api/v1/owners/tester/projects/proj1/issues/1/comments/<<cid>>", {"content": "수정 완료", "original": "고칠 댓글"}),
        ("이슈 댓글 PATCH", "PATCH", f"{B}/issue/1/comments/<<cid>>", {"content": "또 수정", "original": "수정 완료"}),
        ("글 댓글 PATCH", "PATCH", f"{B}/post/1/comment/<<pcid>>", {"content": "글 댓글 수정", "original": "고칠 글 댓글"}),
    ):
        s += [
            Step("anon", method, path, json_body=body, headers={}, note=f"{label} 익명"),
            Step("tester", method, path, json_body=None, headers=TOK, note=f"{label} 본문 없음"),
            Step("tester", method, path, json_body={"content": "x"}, headers=TOK, note=f"{label} original 없음"),
            Step("tester", method, path, json_body={"content": "충돌", "original": "다른 원본"}, headers=TOK, note=f"{label} 충돌"),
            Step("other", method, path, json_body=body, headers=TOK_OTHER, note=f"{label} 남의 것"),
            Step("tester", method, path, json_body=body, headers=TOK, note=f"{label} 정상"),
            Step("tester", method, path.replace("<<cid>>", "99999").replace("<<pcid>>", "99999"), json_body=body, headers=TOK, note=f"{label} 없는 댓글"),
        ]
    for a in ("anon", "tester"):
        s += [Step(a, "GET", f"{B}/issue/1", note=f"고친 뒤 이슈 [{a}]"), Step(a, "GET", f"{B}/post/1", note=f"고친 뒤 글 [{a}]")]
    # 푸시한 브랜치 지우기 (번호는 시험 데이터에 따라 있거나 없다)
    for i in ("1", "2", "99999", "abc"):
        s += [Step(a, "DELETE", f"{B}/pushedBranch/{i}/delete", note=f"푸시한 브랜치 {i} 지우기 [{a}]") for a in ("anon", "tester", "other")]
    # 분류 하나 읽기
    for i in ("1", "2", "99999", "abc"):
        s += [Step(a, "GET", f"{B}/issue/label/category/{i}", headers={"Accept": "application/json"}, note=f"분류 {i} [{a}]") for a in ("anon", "tester", "other")]
    # 풀 리퀘스트의 커밋별 변경
    s += [
        Step("other", "GET", "/other/proj1/newPullRequestForm", capture={"fid": ("body", r"(?s)<option value=\"(\d+)\"[^>]*>\s*other / proj1")}, note="폼 읽기"),
        Step("other", "POST", "/other/proj1/pullRequests", files=mp(toProjectId="1", toBranch="refs/heads/master", fromProjectId="<<fid>>", fromBranch="refs/heads/feature", title="커밋별 변경", body="본문"),
             capture={"pr": ("location", r"/pullRequest/(\d+)")}, note="PR 만들기"),
        Step("tester", "GET", f"{B}/pullRequest/<<pr>>/state", delay=6, note="병합 확인 기다림"),
        Step("tester", "GET", f"{B}/pullRequest/<<pr>>/changes", capture={"sha": ("body", r"/changes/([0-9a-f]{40})")}, note="PR 변경 화면에서 커밋 읽기"),
    ]
    for a in ACTORS:
        s += [Step(a, "GET", f"{B}/pullRequest/<<pr>>/changes/<<sha>>", note=f"커밋별 변경 [{a}]"), Step(a, "GET", f"{B}/pullRequest/<<pr>>/changes/{'0' * 40}", note=f"커밋별 변경 없는 커밋 [{a}]"),
              Step(a, "GET", f"{B}/pullRequest/<<pr>>/changes/abc", note=f"커밋별 변경 잘못된 커밋 [{a}]"), Step(a, "GET", f"{B}/pullRequest/99/changes/<<sha>>", note=f"커밋별 변경 없는 PR [{a}]")]
    # 코드 보기의 메타 정보
    for p in ("/code/!/", "/code/!/src", "/code/!/src%2Fmain", "/code/master/!/", "/code/master/!/src", "/code/master/!/README.md", "/code/master/!/nope", "/code/nope/!/", "/code/dev/!/src/main"):
        s += [Step(a, "GET", B + p, note=f"{p} [{a}]") for a in ("anon", "tester", "other")]
    # 커밋 코멘트를 옛 주소로 지우기
    c = H[1]
    s += [
        Step("other", "POST", f"{B}/commit/{c}/comments", files=mp(contents="지울 코멘트"), capture={"rc": ("location", r"#comment-(\d+)")}, note="지울 커밋 코멘트"),
        Step("tester", "POST", f"{B}/commit/{c}/comments", files=mp(contents="지울 코멘트 둘"), capture={"rc2": ("location", r"#comment-(\d+)")}, note="지울 커밋 코멘트 둘"),
    ]
    for rid in ("rc", "rc2", "99999"):
        v = rid if rid.startswith("rc") else rid
        pid = f"<<{rid}>>" if rid.startswith("rc") else rid
        s += [Step(a, "DELETE", f"{B}/commit/{c}/comments/{pid}/delete", note=f"옛 주소로 코멘트 {rid} 지우기 [{a}]") for a in ("anon", "third", "tester", "other")]
    s += [Step(a, "GET", f"{B}/commit/{c}", note=f"지운 뒤 커밋 [{a}]") for a in ("anon", "tester")]
    # 브랜치 지우기
    s += [
        Step("anon", "DELETE", f"{B}/code/dev/", note="브랜치 지우기 익명"),
        Step("third", "DELETE", f"{B}/code/dev/", note="브랜치 지우기 권한 없음"),
        Step("other", "DELETE", f"{B}/code/dev/", note="브랜치 지우기 구성원"),
        Step("tester", "DELETE", f"{B}/code/nope/", note="브랜치 지우기 없는 브랜치"),
        Step("tester", "DELETE", f"{B}/code/dev/", note="브랜치 지우기"),
        Step("tester", "DELETE", f"{B}/code/dev/", note="브랜치 지우기 다시"),
        Step("tester", "DELETE", f"{B}/code/master/", note="브랜치 지우기 기본 브랜치"),
        Step("tester", "GET", f"{B}/branches", note="지운 뒤 브랜치"),
        Step("tester", "GET", f"{B}/code", note="지운 뒤 코드"),
    ]
    # svn HEAD와 POST
    for p in ("/svn/tester/svn1", "/svn/tester/svn1/trunk", "/svn/tester/nope", "/svn/nobody/none"):
        s += [Step(a, m, p, note=f"{m} {p} [{a}]") for a in ("anon", "tester") for m in ("HEAD", "POST")]
    return s
