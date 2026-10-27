"""다른 시나리오가 닿지 않은 남은 라우트를 부른다: 추천 취소, 기본 브랜치 지정, 초기화, 이메일 확인, 가입 확인, 소셜 로그인 거절, 정적 파일.
data2 상태에서 한 번만 실행한다(기본 브랜치를 바꾼다)."""
from harness import Step
from data1 import login

B = "/tester/proj1"


def steps():
    s = []
    s += [login("tester"), login("other"), login("admin", "admin1234")]
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    s += [
        Step("tester", "POST", f"{B}/issue/1/vote", note="이슈 추천"),
        Step("tester", "POST", f"{B}/issue/1/unvote", note="이슈 추천 취소"),
        Step("tester", "POST", f"{B}/issue/1/unvote", note="이슈 추천 취소 다시"),
        Step("anon", "POST", f"{B}/issue/1/unvote", note="이슈 추천 취소 익명"),
        Step("tester", "POST", f"{B}/issue/99/unvote", note="이슈 추천 취소 없는 이슈"),
        Step("other", "POST", f"{B}/issue/1/unvote", note="이슈 추천 취소 안 한 사용자"),
        Step("tester", "POST", f"{B}/issue/1/comment/1/unvote", note="댓글 추천 취소"),
        Step("tester", "POST", f"{B}/issue/1/comment/99999/unvote", note="댓글 추천 취소 없는 댓글"),
        Step("anon", "POST", f"{B}/issue/1/comment/1/unvote", note="댓글 추천 취소 익명"),
        Step("tester", "POST", f"{B}/code/dev/setAsDefault", note="기본 브랜치 지정"),
        Step("tester", "POST", f"{B}/code/dev/setAsDefault", note="기본 브랜치 다시"),
        Step("tester", "POST", f"{B}/code/nobranch/setAsDefault", note="기본 브랜치 없는 브랜치"),
        Step("other", "POST", f"{B}/code/master/setAsDefault", note="기본 브랜치 권한 없음"),
        Step("anon", "POST", f"{B}/code/master/setAsDefault", note="기본 브랜치 익명"),
        Step("tester", "GET", f"{B}/code", note="기본 브랜치 뒤 코드"),
        Step("tester", "GET", f"{B}/branches", note="기본 브랜치 뒤 브랜치"),
        Step("tester", "POST", f"{B}/code/master/setAsDefault", note="기본 브랜치 되돌리기"),
        Step("anon", "GET", "/_init", note="초기화"),
        Step("admin", "GET", "/_init", note="초기화 관리자"),
        Step("anon", "GET", "/verify/tester/abc", note="가입 확인 틀린 코드"),
        Step("anon", "GET", "/verify/nobody/abc", note="가입 확인 없는 사용자"),
        Step("anon", "GET", "/user/email/confirm/1/abc", note="이메일 확인 틀린 토큰"),
        Step("anon", "GET", "/user/email/confirm/99999/abc", note="이메일 확인 없는 번호"),
        Step("anon", "GET", "/authenticate/github/denied", note="소셜 로그인 거절"),
        Step("anon", "GET", "/authenticate/nope/denied", note="소셜 로그인 거절 알 수 없는 제공자"),
        Step("anon", "GET", "/favicon.ico", note="파비콘"),
        Step("anon", "GET", "/assets/images/favicon.ico", note="정적 파비콘"),
        Step("anon", "GET", "/assets/nope.js", note="없는 정적 파일"),
        Step("anon", "POST", "/!svn-fake/sevice/", note="svn 가짜 서비스"),
        Step("anon", "GET", "/!svn-fake/sevice/", note="svn 가짜 서비스 GET"),
    ]
    return s
