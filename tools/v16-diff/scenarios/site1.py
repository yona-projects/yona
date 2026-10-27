"""사이트 관리자 화면의 남은 변경 요청과 내보내기, 가져오기, 메일 대상 목록, 삭제를 여러 사용자로 보낸다. forms1이 다룬 계정 잠금과 관리자 지정은 뺀다.
data2 상태에서 한 번만 실행한다(사용자와 프로젝트를 지운다). 지우는 요청은 가장 뒤에 둔다."""
from harness import Step
from data1 import login

JSON = {"Accept": "application/json"}


def steps():
    s = []
    s += [login("tester"), login("other"), login("third"), login("admin", "admin1234")]
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    # 읽기
    for actor in ("anon", "tester", "admin"):
        s += [
            Step(actor, "GET", "/sites/noAvatarUsers", note=f"아바타 없는 사용자 [{actor}]"),
            Step(actor, "GET", "/sites/export", compare_body=False, note=f"내보내기 [{actor}]"),
            Step(actor, "GET", "/sites/data", note=f"데이터 화면 [{actor}]"),
        ]
    # 메일 대상
    s += [
        Step("anon", "POST", "/sites/mailList", data={"all": "true"}, headers=JSON, note="메일 대상 익명"),
        Step("tester", "POST", "/sites/mailList", data={"all": "true"}, headers=JSON, note="메일 대상 권한 없음"),
        Step("admin", "POST", "/sites/mailList", data={"all": "true"}, headers=JSON, note="메일 대상 전체"),
        Step("admin", "POST", "/sites/mailList", data={"all": "false"}, headers=JSON, note="메일 대상 all=false"),
        Step("admin", "POST", "/sites/mailList", data={"p1": "tester/proj1"}, headers=JSON, note="메일 대상 프로젝트"),
        Step("admin", "POST", "/sites/mailList", data={"p1": "tester/proj1", "p2": "tester/priv1", "p3": "nobody/none"}, headers=JSON, note="메일 대상 여러 프로젝트"),
        Step("admin", "POST", "/sites/mailList", data={"p1": "badformat"}, headers=JSON, note="메일 대상 형식 틀림"),
        Step("admin", "POST", "/sites/mailList", data={"all": "true"}, headers={"Accept": "text/html"}, note="메일 대상 Accept 아님"),
        Step("admin", "POST", "/sites/mailList", json_body={"all": "true"}, headers=JSON, note="메일 대상 JSON 본문"),
        Step("admin", "POST", "/sites/mailList", headers=JSON, note="메일 대상 본문 없음"),
    ]
    # 가져오기
    s += [
        Step("tester", "POST", "/sites/import", files={"data": ("d.json", b"{}", "application/json")}, note="가져오기 권한 없음"),
        Step("admin", "POST", "/sites/import", files={"other": ("d.json", b"{}", "application/json")}, note="가져오기 파일 없음"),
        Step("admin", "POST", "/sites/import", files={"data": ("d.json", b"not json", "application/json")}, note="가져오기 잘못된 파일"),
    ]
    # 아바타 지정
    s += [
        Step("tester", "POST", "/sites/setAttachmentToUserAvatar", json_body={"avatarFileId": 1, "email": "tester@example.com"}, note="아바타 지정 권한 없음"),
        Step("admin", "POST", "/sites/setAttachmentToUserAvatar", note="아바타 지정 본문 없음"),
        Step("admin", "POST", "/sites/setAttachmentToUserAvatar", json_body={"email": "x"}, note="아바타 지정 파일 번호 없음"),
    ]
    # 임포트 화면
    s += [
        Step("anon", "GET", "/_import", note="프로젝트 가져오기 화면 익명"),
        Step("tester", "GET", "/_import", note="프로젝트 가져오기 화면"),
        Step("tester", "POST", "/_import", data={"owner": "tester", "name": "imp1"}, note="프로젝트 가져오기 본문 부족"),
    ]
    # 삭제 (가장 뒤)
    s += [
        Step("admin", "GET", "/sites/userList", capture={"uid_del": ("body", r"/sites/user/delete(\d+)")}, note="삭제할 사용자 번호"),
        Step("admin", "GET", "/sites/projectList", capture={"pid_del": ("body", r"/sites/project/delete/(\d+)")}, note="삭제할 프로젝트 번호"),
        Step("anon", "DELETE", "/sites/user/delete<<uid_del>>", note="사용자 삭제 익명"),
        Step("tester", "DELETE", "/sites/user/delete<<uid_del>>", note="사용자 삭제 권한 없음"),
        Step("admin", "DELETE", "/sites/user/delete<<uid_third>>", note="사용자 삭제 third"),
        Step("admin", "DELETE", "/sites/user/delete<<uid_third>>", note="사용자 삭제 다시"),
        Step("admin", "DELETE", "/sites/user/delete99999", note="사용자 삭제 없는 번호"),
        Step("admin", "DELETE", "/sites/user/deleteabc", note="사용자 삭제 잘못된 번호"),
        Step("anon", "DELETE", "/sites/project/delete/<<pid_del>>", note="프로젝트 삭제 익명"),
        Step("tester", "DELETE", "/sites/project/delete/<<pid_del>>", note="프로젝트 삭제 권한 없음"),
        Step("admin", "DELETE", "/sites/project/delete/<<pid_del>>", note="프로젝트 삭제"),
        Step("admin", "DELETE", "/sites/project/delete/<<pid_del>>", note="프로젝트 삭제 다시"),
        Step("admin", "DELETE", "/sites/project/delete/99999", note="프로젝트 삭제 없는 번호"),
        Step("admin", "GET", "/sites/userList", note="사용자 목록 뒤"),
        Step("admin", "GET", "/sites/projectList", note="프로젝트 목록 뒤"),
        Step("anon", "GET", "/", note="홈 뒤"),
    ]
    return s
