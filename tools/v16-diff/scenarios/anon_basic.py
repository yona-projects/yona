"""로그인하지 않은 방문자가 열 수 있는 기본 화면과 없는 주소, 거부 응답이다. 데이터가 필요 없다."""
from harness import Step


def steps():
    gets = [
        "/", "/users/loginform", "/users/login", "/users/signupform", "/lostPassword", "/resetPassword", "/projects", "/orgs",
        "/projectform", "/organizations/new", "/_help", "/_UIKit", "/messages.js", "/search", "/search?keyword=yona",
        "/notification", "/notifications", "/user/editform", "/user/issues", "/user/sidebar", "/user/files", "/sites/userList",
        "/sites/projectList", "/sites/mail", "/sites/data", "/sites/update", "/migration", "/_import", "/restricted",
        "/user/isUsed?name=admin", "/user/isEmailExist?email=a@b.com", "/-_-api", "/-_-api/v1/", "/-_-api/v1/hello",
        "/-_-api/v1/users", "/labels", "/categories", "/authenticate/github", "/logout", "/users/logout",
        "/nope", "/nope/", "/nope/nope", "/nope/nope/", "/nope/nope/issues", "/nope/nope/code", "/a/b/c/d",
        "/svn/nope/nope", "/admin", "/admin/", "/unwatch",
    ]
    return [Step("anon", "GET", p) for p in gets] + [
        Step("anon", "POST", "/users/login", data={"loginIdOrEmail": "nobody", "password": "x"}, note="없는 계정"),
        Step("anon", "POST", "/users/login", data={"loginIdOrEmail": "", "password": ""}, note="빈 값"),
        Step("anon", "POST", "/users/signup", data={"loginId": "", "name": "", "email": "", "password": ""}, note="빈 가입"),
        Step("anon", "POST", "/lostPassword", data={"loginId": "nobody", "email": "x@y.z"}, note="없는 계정 재설정"),
        Step("anon", "POST", "/projects", data={"owner": "x", "name": "y"}, note="익명 프로젝트 생성"),
        Step("anon", "POST", "/watch", data={"resourceType": "project", "resourceId": "1"}, note="익명 구독"),
    ]
