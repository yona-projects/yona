"""최초 설치 직후(`first.sh`가 만든 `first` 상태) 사이트 비밀 키 설정 흐름: 모든 요청이 설정 화면을 받는지, 설정 화면의 검증(관리자 ID, 비밀번호, 확인, 이메일 중복과 형식),
성공하면 나오는 재시작 안내와 그 뒤의 모든 요청이 재시작 안내를 받는지를 대조한다. `first2`가 서버를 다시 띄운 뒤의 모습을 이어서 본다."""
from harness import Step

OK = {"loginId": "admin", "name": "관리자", "email": "root@example.com", "password": "newpass1", "retypedPassword": "newpass1"}


def post(note, **over):
    return Step("anon", "POST", "/", data={**OK, **over}, note=note)


def steps():
    s = []
    # 비밀 키를 정하기 전: 어느 주소든 설정 화면이다
    for p in ("/", "/users/loginform", "/projects", "/tester/proj1", "/assets/stylesheets/yobi.css", "/assets/javascripts/yona-layout.js", "/favicon.ico", "/messages.js",
              "/-_-api/v1/hello", "/svn/tester/proj1", "/lostPassword", "/sites/setting", "/nothing/here/at/all", "/?x=1", "/files/1", "/user/issues/new",
              "/nobody", "/admin", "/orgs", "/user/edit", "/notifications", "/sites/userList", "/tester/proj1/issues", "/tester/proj1/code/master/", "/search", "/_help", "/migration", "/sites/mailList", "/-_-api/v1/users"):
        s.append(Step("anon", "GET", p, note=f"비밀 키 전 GET {p}"))
    for m in ("HEAD", "OPTIONS", "PUT", "DELETE", "PATCH"):
        s.append(Step("anon", m, "/", note=f"비밀 키 전 {m} /"))
    s += [
        Step("anon", "POST", "/users/login", data={"loginIdOrEmail": "admin", "password": "admin"}, note="비밀 키 전 로그인 POST"),
        Step("anon", "POST", "/some/other/path", data={"loginId": "admin"}, note="비밀 키 전 다른 경로 POST(검증 실패)"),
    ]
    # 설정 화면의 검증
    s += [
        Step("anon", "POST", "/", data={}, note="빈 본문"),
        post("관리자 ID 다름", loginId="root"),
        post("관리자 ID 없음", loginId=""),
        post("관리자 ID 공백", loginId=" "),
        post("관리자 ID 특수문자", loginId="root!"),
        post("관리자 ID 한글", loginId="관리자"),
        post("관리자 ID 예약어", loginId="sites"),
        post("관리자 ID 대문자", loginId="ADMIN"),
        post("관리자 ID 뒤 공백", loginId="admin "),
        post("관리자 ID 점", loginId="a.b"),
        post("관리자 ID 밑줄", loginId="a_b"),
        post("비밀번호 없음", password="", retypedPassword=""),
        post("비밀번호 확인 다름", retypedPassword="다름"),
        post("이메일 없음", email=""),
        post("이메일 형식 이상", email="not-an-email"),
        post("이름 없음", name=""),
        post("이름 아주 긺", name="가" * 300),
        Step("anon", "POST", "/", data={**OK, "email": "admin@yona.io"}, note="이미 있는 이메일"),
        Step("anon", "POST", "/", data={**OK, "loginId": "admin", "extra": "무시됨"}, files=None, note="정상 입력 전 추가 필드"),
    ]
    # 성공
    s += [post("정상 입력(성공)")]
    # 성공한 뒤: 모든 요청이 재시작 안내이다
    for p in ("/", "/users/loginform", "/assets/stylesheets/yobi.css", "/-_-api/v1/hello", "/svn/tester/proj1", "/nothing/here"):
        s.append(Step("anon", "GET", p, note=f"성공 뒤 GET {p}"))
    s += [post("성공 뒤 다시 POST"), Step("anon", "POST", "/users/login", data={"loginIdOrEmail": "admin", "password": "newpass1"}, note="성공 뒤 로그인 POST"),
          Step("anon", "HEAD", "/", note="성공 뒤 HEAD /")]
    return s
