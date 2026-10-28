"""`first1` 뒤 서버를 다시 띄운(`servers.sh restart`) 상태: 새 비밀번호로 로그인되고, 옛 비밀번호는 안 되며, 사이트 관리자 정보가 맞는지, 평소 화면이 열리는지 대조한다."""
from harness import Step
from data1 import login


def steps():
    s = [
        Step("anon", "GET", "/", note="재시작 뒤 홈"),
        Step("anon", "GET", "/users/loginform", note="재시작 뒤 로그인 화면"),
        Step("anon", "POST", "/users/login", data={"loginIdOrEmail": "admin", "password": "admin"}, note="옛 비밀번호"),
        Step("anon", "POST", "/users/login", data={"loginIdOrEmail": "root@example.com", "password": "newpass1"}, note="새 이메일로 로그인"),
        login("admin", "newpass1"),
        Step("admin", "GET", "/admin", note="관리자 사용자 화면"),
        Step("admin", "GET", "/sites/userList", note="사용자 목록"),
        Step("admin", "GET", "/sites/setting", note="사이트 설정"),
        Step("admin", "GET", "/user/edit", note="관리자 정보 수정"),
        Step("admin", "GET", "/-_-api/v1/hello", note="API hello"),
        Step("admin", "GET", "/projects", note="프로젝트 목록"),
    ]
    return s
