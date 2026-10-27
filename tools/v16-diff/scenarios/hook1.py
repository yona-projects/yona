"""웹훅 네 종류(SIMPLE, DETAIL_SLACK, DETAIL_HANGOUT_CHAT, JSON)를 만들고 이슈, 글, 댓글 이벤트를 일으킨다. 요청은 httpsink.py(19226, 19326)가 받는다.
data2 상태에서 실행하고 `hookcompare.py`로 대조한다. 푸시와 풀 리퀘스트 웹훅은 `gitpush.sh`와 pr 시나리오로 따로 일으킨다."""
from harness import Step
from data1 import login, mp

B = "/tester/proj1"
URL = "http://127.0.0.1:<<sinkport>>"


def hook(kind, path, secret="s3cret"):
    return Step("tester", "POST", f"{B}/webhooks", data={"payloadUrl": f"{URL}/{path}", "secret": secret, "webhookType": kind, "gitPush": "on"}, note=f"웹훅 {kind}")


def steps():
    s = []
    s += [login("tester"), login("other"), login("admin", "admin1234")]
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    s += [hook("SIMPLE", "simple"), hook("DETAIL_SLACK", "slack"), hook("DETAIL_HANGOUT_CHAT", "hangout"), hook("JSON", "json", "")]
    s += [
        Step("tester", "POST", f"{B}/issues/latest", files=mp(title="웹훅 이슈", body="본문 @other", isDraft="false", assigneeLoginId="other", milestoneId="1"), note="새 이슈"),
        Step("other", "POST", f"{B}/issue/4/comments", files=mp(contents="웹훅 댓글 **굵게**"), note="새 댓글"),
        Step("tester", "POST", f"{B}/issue/4/comments", files=mp(contents="닫습니다", withStateTransition="close"), note="상태 바꾸는 댓글"),
        Step("tester", "POST", f"{B}/issue/4/edit", files=mp(title="웹훅 이슈 수정", body="본문 수정", assigneeLoginId="tester", milestoneId="1", isDraft="false", dueDate=""), note="이슈 수정"),
        Step("tester", "POST", f"{B}/posts", files=mp(title="웹훅 글", body="글 본문", issueTemplate="", branch="", path="", lineEnding="UNDEFINED"), note="새 글"),
        Step("other", "POST", f"{B}/post/1/comment", files=mp(contents="글 댓글 웹훅"), note="글 댓글"),
        Step("tester", "POST", f"{B}/issue/4/nextstate", note="다음 상태"),
        Step("tester", "POST", f"{B}/issue/4/delete", note="이슈 삭제"),
    ]
    return s
