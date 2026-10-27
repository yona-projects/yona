"""알림 메일이 나가는 남은 흐름을 일으킨다: 커밋 코멘트(Git, Subversion), 비밀번호 재설정 요청, 사이트 메일 보내기, 댓글 구독. 푸시 알림은 `gitpush.sh`를 먼저 돌린다.
data3 상태에서 실행하고 `mailcompare.py`로 메일을 대조한다."""
import subprocess

from harness import Step
from data1 import login, mp

B = "/tester/proj1"


def _hashes():
    return subprocess.run(["git", "-C", "/home/jiho/yona-convert/yona-diff-work/git-tester-proj1-base", "log", "--reverse", "--format=%H"], capture_output=True, text=True, check=True).stdout.split()


H = _hashes()


def steps():
    s = []
    s += [login("tester"), login("other"), login("third"), login("admin", "admin1234")]
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    s += [
        Step("other", "POST", f"{B}/watch", note="프로젝트 구독"),
        Step("tester", "POST", f"{B}/commit/{H[1]}/comments", files=mp(contents="커밋 전체 의견 @other 확인"), note="커밋 코멘트"),
        Step("other", "POST", f"{B}/commit/{H[1]}/comments", files=mp(contents="범위 의견", path="README.md", startLine=1, startColumn=0, startSide="B", endLine=2, endColumn=3, endSide="B"), note="커밋 범위 코멘트"),
        Step("third", "POST", f"{B}/commit/{H[0]}/comments", files=mp(contents="세 번째 사람 의견"), note="커밋 코멘트 제3자"),
        Step("tester", "POST", "/tester/svn1/commit/3/comments", files=mp(contents="svn 커밋 의견 @other"), note="SVN 커밋 코멘트"),
        Step("other", "POST", "/tester/svn1/commit/3/comments", files=mp(contents="svn 답글"), note="SVN 커밋 코멘트 답"),
        Step("anon", "POST", "/lostPassword", data={"loginId": "tester", "emailAddress": "tester@example.com"}, note="비밀번호 재설정 요청"),
        Step("anon", "POST", "/lostPassword", data={"loginId": "tester", "emailAddress": "wrong@example.com"}, note="비밀번호 재설정 틀린 메일"),
        Step("anon", "POST", "/lostPassword", data={"loginId": "nobody", "emailAddress": "x@y.z"}, note="비밀번호 재설정 없는 사용자"),
        Step("admin", "POST", "/sites/mail", data={"to": "tester@example.com", "subject": "관리자 메일", "body": "본문입니다\n둘째 줄"}, note="사이트 메일"),
        Step("tester", "POST", "/sites/mail", data={"to": "tester@example.com", "subject": "권한 없음", "body": "x"}, note="사이트 메일 권한 없음"),
        Step("tester", "POST", f"{B}/issues/latest", files=mp(title="구독 시험", body="본문 @third", isDraft="false"), note="이슈 만들기"),
        Step("tester", "POST", "/watch?resource.type=issue_post&resource.id=<<issue_watch>>", note="이슈 구독"),
    ]
    return s
