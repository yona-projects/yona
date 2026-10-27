"""댓글 수 저장 규칙을 알아내는 시나리오이다. 댓글을 더하고 고치고 지우고 추천하며 목록의 숫자를 계속 읽는다. data1 상태에서 한 번만 실행한다."""
from harness import Step
from data1 import mp, login


def steps():
    s = [login("tester"), login("other")]
    issues = "/tester/proj1/issues"
    posts = "/tester/proj1/posts"

    def show():
        return [Step("tester", "GET", issues, note="이슈 목록"), Step("tester", "GET", posts, note="글 목록")]

    s.append(Step("tester", "POST", "/tester/proj1/issues/latest", files=mp(title="댓글 수 시험", body="본문", isDraft="false"), note="이슈 4"))
    s.append(Step("tester", "POST", "/tester/proj1/posts", files=mp(title="댓글 수 시험 글", body="본문", issueTemplate="", branch="", path="", lineEnding="UNDEFINED"), note="글 3"))
    s += show()
    ops = [("tester", "첫"), ("other", "둘째"), ("tester", "셋째"), ("other", "넷째")]
    for who, text in ops:
        s.append(Step(who, "POST", "/tester/proj1/issue/4/comments", files=mp(contents=text), note=f"이슈 댓글 {text}"))
        s.append(Step(who, "POST", "/tester/proj1/post/3/comment", files=mp(contents=text), note=f"글 댓글 {text}"))
        s += show()
    # 고치기, 지우기, 추천
    s.append(Step("tester", "POST", "/tester/proj1/issue/4/comments/5", files=mp(contents="고친 댓글"), note="이슈 댓글 고치기"))
    s += show()
    s.append(Step("tester", "DELETE", "/tester/proj1/issue/4/comment/5/delete", note="이슈 댓글 지우기"))
    s.append(Step("tester", "DELETE", "/tester/proj1/post/3/comment/6/delete", note="글 댓글 지우기"))
    s += show()
    s.append(Step("other", "POST", "/tester/proj1/issue/4/vote", note="이슈 추천"))
    s += show()
    s.append(Step("tester", "POST", "/tester/proj1/issue/4/comments", files=mp(contents="다섯째"), note="이슈 댓글 다섯째"))
    s.append(Step("tester", "POST", "/tester/proj1/post/3/comment", files=mp(contents="다섯째"), note="글 댓글 다섯째"))
    s += show()
    s.append(Step("tester", "POST", "/tester/proj1/issue/4/edit", files=mp(title="댓글 수 시험(수정)", body="본문 수정", isDraft="false"), note="이슈 수정"))
    s += show()
    return s
