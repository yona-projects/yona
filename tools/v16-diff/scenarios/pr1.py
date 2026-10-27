"""포크(other/proj1)에서 원본(tester/proj1)으로 가는 풀 리퀘스트의 만들기, 보기, 댓글, 리뷰, 닫기, 병합을 모든 사용자로 해 본다.
pr1a와 pr1.sh를 먼저 실행해야 한다. 풀 리퀘스트 번호는 응답에서 읽어 쓴다."""
from harness import Step
from data1 import mp, login

ACTORS = ["anon", "third", "other", "tester", "admin"]
FORK = "other / proj1"


def get_all(path, label=""):
    return [Step(a, "GET", path, note=f"{label or path} [{a}]") for a in ACTORS]


def steps():
    s = []
    s += [login("tester"), login("other"), login("third"), login("admin", "admin1234")]
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    s.append(Step("other", "GET", "/other/proj1/newPullRequestForm", capture={"fid": ("body", r"(?s)<option value=\"(\d+)\"[^>]*>\s*other / proj1")}, note="폼 읽기"))
    s.append(Step("tester", "GET", "/tester/proj1/newPullRequestForm", note="원본에서 폼"))
    s.append(Step("anon", "GET", "/other/proj1/newPullRequestForm", note="폼 익명"))
    s += [
        Step("other", "GET", "/other/proj1/newPullRequest/mergeResult?fromProjectId=<<fid>>&fromBranch=refs/heads/feature&toProjectId=1&toBranch=refs/heads/master", note="병합 결과 미리보기"),
        Step("other", "GET", "/other/proj1/newPullRequest/mergeResult?fromProjectId=<<fid>>&fromBranch=refs/heads/conflict&toProjectId=1&toBranch=refs/heads/master", note="충돌 미리보기"),
        Step("other", "GET", "/other/proj1/newPullRequest/mergeResult?fromProjectId=<<fid>>&fromBranch=refs/heads/nope&toProjectId=1&toBranch=refs/heads/master", note="없는 브랜치 미리보기"),
        Step("other", "GET", "/other/proj1/newPullRequest/mergeResult", note="미리보기 인자 없음"),
    ]
    base = dict(toProjectId="1", toBranch="refs/heads/master", fromProjectId="<<fid>>", fromBranch="refs/heads/feature", title="기능 추가", body="풀 리퀘스트 본문 @tester 확인", isUpdated="false")
    s += [
        Step("anon", "POST", "/other/proj1/pullRequests", files=mp(**base), note="PR 익명"),
        Step("third", "POST", "/other/proj1/pullRequests", files=mp(**base), note="PR 남의 포크"),
        Step("other", "POST", "/other/proj1/pullRequests", files=mp(**{**base, "title": ""}), note="PR 제목 없음"),
        Step("other", "POST", "/other/proj1/pullRequests", files=mp(**{**base, "fromBranch": "refs/heads/nope"}), note="PR 없는 브랜치"),
        Step("other", "POST", "/other/proj1/pullRequests", files=mp(**{**base, "fromBranch": "refs/heads/master"}), note="PR 변경 없음"),
        Step("other", "POST", "/other/proj1/pullRequests", files=mp(**{**base, "toProjectId": "<<fid>>"}), note="PR 같은 프로젝트"),
        Step("other", "POST", "/other/proj1/pullRequests", files=mp(**{**base, "fromBranch": "refs/heads/conflict", "title": "충돌 PR"}), capture={"pr_conflict": ("location", r"/pullRequest/(\d+)")}, note="PR 충돌"),
        Step("other", "POST", "/other/proj1/pullRequests", files=mp(**base), capture={"pr": ("location", r"/pullRequest/(\d+)")}, note="PR 만들기"),
        Step("other", "POST", "/other/proj1/pullRequests", files=mp(**base), note="PR 같은 것 다시"),
    ]
    s.append(Step("tester", "GET", "/tester/proj1/pullRequests", delay=8, note="병합 확인 행위자가 끝나기를 기다림"))
    s += get_all("/tester/proj1/pullRequests", "만든 직후 열린 목록")
    s += get_all("/tester/proj1/closedPullRequests", "만든 직후 닫힌 목록")
    # 보기
    s.append(Step("tester", "GET", "/tester/proj1/pullRequest/<<pr>>/state", delay=6, note="병합 확인이 끝나기를 기다림"))
    for path in ("/tester/proj1/pullRequest/<<pr>>", "/tester/proj1/pullRequest/<<pr>>/changes", "/tester/proj1/pullRequest/<<pr>>/state",
                 "/tester/proj1/pullRequest/<<pr>>/editform", "/tester/proj1/pullRequest/<<pr_conflict>>", "/tester/proj1/pullRequest/99",
                 "/tester/proj1/pullRequests", "/tester/proj1/closedPullRequests", "/tester/proj1/sentPullRequests", "/other/proj1/sentPullRequests",
                 "/tester/proj1/mentionListAtPullRequest?pullRequestId=<<pr>>&mentionType=user", "/tester/proj1/mentionListAtPullRequest?pullRequestId=<<pr>>&mentionType=issue&query=1"):
        s += get_all(path)
    # 댓글과 리뷰
    s += [
        Step("anon", "POST", "/tester/proj1/pullRequest/<<pr>>/comments", files=mp(contents="익명"), note="PR 댓글 익명"),
        Step("third", "POST", "/tester/proj1/pullRequest/<<pr>>/comments", files=mp(contents="제3자 댓글"), note="PR 댓글 제3자"),
        Step("tester", "POST", "/tester/proj1/pullRequest/<<pr>>/comments", files=mp(contents=""), note="PR 댓글 빈 값"),
        Step("tester", "POST", "/tester/proj1/pullRequest/<<pr>>/comments", files=mp(contents="리뷰합니다 @other"), capture={"prc": ("location", r"#comment-(\d+)")}, note="PR 댓글"),
        Step("other", "POST", "/tester/proj1/pullRequest/<<pr>>/comments", files=mp(contents="답변"), note="PR 댓글 답"),
        Step("tester", "POST", "/tester/proj1/pullRequest/99/comments", files=mp(contents="없는 PR"), note="PR 댓글 없는 PR"),
        Step("third", "POST", "/tester/proj1/pullRequest/<<pr>>/review", note="리뷰 권한 없음"),
        Step("tester", "POST", "/tester/proj1/pullRequest/<<pr>>/review", note="리뷰"),
        Step("tester", "POST", "/tester/proj1/pullRequest/<<pr>>/unreview", note="리뷰 취소"),
        Step("tester", "POST", "/tester/proj1/pullRequest/<<pr>>/review", note="리뷰 다시"),
        Step("tester", "POST", "/tester/proj1/pullRequest/<<pr>>/edit", files=mp(title="기능 추가(수정)", body="수정한 본문"), note="PR 수정"),
        Step("third", "POST", "/tester/proj1/pullRequest/<<pr>>/edit", files=mp(title="제3자 수정", body="x"), note="PR 수정 권한 없음"),
    ]
    for path in ("/tester/proj1/pullRequest/<<pr>>", "/tester/proj1/pullRequest/<<pr>>/changes", "/tester/proj1/pullRequests?filter=기능"):
        s += get_all(path, "댓글 뒤 " + path)
    # 상태 바꾸기
    s += [
        Step("third", "POST", "/tester/proj1/pullRequest/<<pr_conflict>>/close", note="닫기 권한 없음"),
        Step("tester", "POST", "/tester/proj1/pullRequest/<<pr_conflict>>/close", note="충돌 PR 닫기"),
        Step("tester", "POST", "/tester/proj1/pullRequest/<<pr_conflict>>/open", note="충돌 PR 열기"),
        Step("tester", "POST", "/tester/proj1/pullRequest/<<pr_conflict>>/accept", note="충돌 PR 병합"),
        Step("third", "POST", "/tester/proj1/pullRequest/<<pr>>/accept", note="병합 권한 없음"),
        Step("tester", "POST", "/tester/proj1/pullRequest/<<pr>>/accept", note="병합"),
        Step("tester", "GET", "/tester/proj1/pullRequest/<<pr>>", delay=6, note="병합 뒤 기다림"),
        Step("tester", "POST", "/tester/proj1/pullRequest/<<pr>>/accept", note="병합 다시"),
        Step("tester", "POST", "/tester/proj1/pullRequest/<<pr>>/close", note="병합된 PR 닫기"),
        Step("other", "DELETE", "/tester/proj1/pullRequest/<<pr>>/deletefrombranch", note="보낸 브랜치 지우기"),
        Step("other", "POST", "/tester/proj1/pullRequest/<<pr>>/restorefrombranch", note="보낸 브랜치 되살리기"),
    ]
    for path in ("/tester/proj1/pullRequest/<<pr>>", "/tester/proj1/pullRequest/<<pr>>/changes", "/tester/proj1/pullRequest/<<pr_conflict>>", "/tester/proj1/pullRequests",
                 "/tester/proj1/closedPullRequests", "/tester/proj1/sentPullRequests", "/tester/proj1/code", "/tester/proj1/commits", "/tester/proj1/branches", "/tester/proj1/code/master/src/main",
                 "/tester/proj1/issues", "/tester", "/other", "/", "/notification"):
        s += get_all(path, "병합 뒤 " + path)
    return s
