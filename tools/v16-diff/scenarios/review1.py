"""코드 리뷰: 커밋과 풀 리퀘스트에 범위 코멘트를 달고 스레드에 답하고 열고 닫고 지우며, 리뷰 목록(상태, 작성자, 참여자, 엑셀)과 화면을 읽는다.
pr1a와 pr1.sh를 먼저 돌린 data2 상태에서 실행한다. 커밋 해시는 gitprep.sh가 만든 저장소에서 읽는다."""
import subprocess

from harness import Step
from data1 import login, mp

B = "/tester/proj1"
ACTORS = ["anon", "tester", "other", "third", "admin"]


def _hashes():
    return subprocess.run(["git", "-C", "/home/jiho/yona-convert/yona-diff-work/git-tester-proj1-base", "log", "--reverse", "--format=%H"], capture_output=True, text=True, check=True).stdout.split()


H = _hashes()


def rng(path, start, end, side="B"):
    return dict(path=path, startLine=start, startColumn=0, startSide=side, endLine=end, endColumn=4, endSide=side)


def steps():
    s = []
    s += [login("tester"), login("other"), login("third"), login("admin", "admin1234")]
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    s.append(Step("other", "GET", "/other/proj1/newPullRequestForm", capture={"fid": ("body", r"(?s)<option value=\"(\d+)\"[^>]*>\s*other / proj1")}, note="폼 읽기"))
    base = dict(toProjectId="1", toBranch="refs/heads/master", fromProjectId="<<fid>>", fromBranch="refs/heads/feature", title="리뷰 시험 PR", body="본문")
    s.append(Step("other", "POST", "/other/proj1/pullRequests", files=mp(**base), capture={"pr": ("location", r"/pullRequest/(\d+)")}, note="PR 만들기"))
    s.append(Step("tester", "GET", f"{B}/pullRequest/<<pr>>/state", delay=6, note="병합 확인 기다림"))
    # 커밋 코멘트
    c = H[1]
    s += [
        Step("tester", "POST", f"{B}/commit/{c}/comments", files=mp(contents="범위 의견 `코드`", **rng("README.md", 7, 7)), note="커밋 범위 코멘트"),
        Step("tester", "GET", f"{B}/commit/{c}", capture={"th1": ("body", r'id="thread-(\d+)"')}, note="커밋 보기"),
        Step("other", "POST", f"{B}/commit/{c}/comments", files=mp(contents="답글 @tester", **{"thread.id": "<<th1>>"}), note="스레드 답글"),
        Step("third", "POST", f"{B}/commit/{c}/comments", files=mp(contents="제3자 새 스레드", **rng("src/main/Util.java", 2, 3)), note="다른 파일 범위 코멘트"),
        Step("tester", "POST", f"{B}/commit/{c}/comments", files=mp(contents="전체 의견"), note="범위 없는 코멘트"),
        Step("anon", "POST", f"{B}/commit/{c}/comments", files=mp(contents="익명"), note="익명 코멘트"),
        Step("tester", "POST", f"{B}/commit/{c}/comments", files=mp(contents=""), note="빈 코멘트"),
        Step("tester", "POST", f"{B}/commit/{c}/comments", files=mp(contents="범위 이상", path="README.md", startLine="x", startSide="B", endLine=1, endSide="B"), note="잘못된 범위"),
    ]
    for a in ACTORS:
        s.append(Step(a, "GET", f"{B}/commit/{c}", note=f"커밋 보기 [{a}]"))
    # 풀 리퀘스트 코멘트
    s += [
        Step("tester", "POST", f"{B}/pullRequest/<<pr>>/comments", files=mp(contents="PR 범위 의견", **rng("src/main/Feature.java", 1, 2)), note="PR 범위 코멘트"),
        Step("tester", "POST", f"{B}/pullRequest/<<pr>>/comments", files=mp(contents="PR 전체 의견"), note="PR 전체 코멘트"),
        Step("other", "GET", f"{B}/pullRequest/<<pr>>/changes", capture={"th2": ("body", r'id="thread-(\d+)"')}, note="PR 변경 보기"),
        Step("other", "POST", f"{B}/pullRequest/<<pr>>/comments", files=mp(contents="PR 답글", **{"thread.id": "<<th2>>"}), note="PR 스레드 답글"),
    ]
    for a in ACTORS:
        s.append(Step(a, "GET", f"{B}/pullRequest/<<pr>>/changes", note=f"PR 변경 [{a}]"))
        s.append(Step(a, "GET", f"{B}/pullRequest/<<pr>>", note=f"PR 보기 [{a}]"))
    # 스레드 열고 닫기
    s += [
        Step("third", "POST", "/threads/<<th1>>/close", note="스레드 닫기 권한 없음"),
        Step("anon", "POST", "/threads/<<th1>>/close", note="스레드 닫기 익명"),
        Step("tester", "POST", "/threads/<<th1>>/close", note="스레드 닫기"),
        Step("tester", "POST", "/threads/<<th1>>/close", note="스레드 다시 닫기"),
        Step("tester", "POST", "/threads/99999/close", note="스레드 닫기 없는 번호"),
        Step("other", "POST", "/threads/<<th1>>/open", note="스레드 열기"),
        Step("tester", "POST", "/threads/<<th2>>/close", note="PR 스레드 닫기"),
    ]
    # 리뷰 목록
    paths = [f"{B}/reviews", f"{B}/reviews?state=open", f"{B}/reviews?state=closed", f"{B}/reviews?state=all", f"{B}/reviews?authorId=<<uid_tester>>", f"{B}/reviews?participantId=<<uid_other>>",
             f"{B}/reviews?pageNum=2", f"{B}/reviews?format=xls", f"{B}/reviews?state=bogus", "/tester/priv1/reviews"]
    for p in paths:
        for a in ACTORS:
            s.append(Step(a, "GET", p, note=f"{p} [{a}]"))
    for a in ACTORS:
        s.append(Step(a, "GET", f"{B}/commit/{c}", note=f"스레드 상태 뒤 커밋 [{a}]"))
        s.append(Step(a, "GET", f"{B}/pullRequest/<<pr>>/changes", note=f"스레드 상태 뒤 PR 변경 [{a}]"))
    # 지우기
    s += [
        Step("third", "DELETE", "/comments/review_comment/99999", note="댓글 삭제 없는 것"),
        Step("anon", "GET", f"{B}/mentionListAtCommitDiff?commitId={c}", note="커밋 멘션"),
        Step("tester", "GET", f"{B}/mentionListAtCommitDiff?commitId={c}", note="커밋 멘션 tester"),
        Step("tester", "GET", f"{B}/mentionListAtPullRequest?pullRequestId=<<pr>>", note="PR 멘션"),
    ]
    return s
