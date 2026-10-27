"""조직 소유 프로젝트와 한글 이름 프로젝트를 여러 역할로 읽고 쓴다. 조직 구성원 역할, 비공개와 보호 프로젝트의 가시성, 한글 주소의 인코딩, 조직 화면(이슈, 글, 검색)을 본다.
data2 상태에서 한 번만 실행한다(조직 `corp1`과 프로젝트를 만든다)."""
from harness import Step
from data1 import login, mp, project
from forms1 import mpf

ACTORS = ["anon", "tester", "other", "third", "admin"]
JSON = {"Accept": "application/json"}


def sweep(paths, label):
    return [Step(a, "GET", p, note=f"{label} {p} [{a}]") for p in paths for a in ACTORS]


def steps():
    s = []
    s += [login("tester"), login("other"), login("third"), login("admin", "admin1234")]
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    # 조직과 구성원
    s += [
        Step("tester", "POST", "/organizations/new", data={"name": "corp1", "descr": "회사 조직"}, note="조직 만들기"),
        Step("tester", "POST", "/organizations/new", data={"name": "corp1", "descr": "중복"}, note="조직 이름 중복"),
        Step("tester", "POST", "/organizations/new", data={"name": "한글조직", "descr": "한글 이름 조직"}, note="한글 이름 조직"),
        Step("tester", "POST", "/organizations/corp1/members", data={"loginId": "other"}, note="구성원 other"),
        Step("tester", "POST", "/organizations/corp1/members", data={"loginId": "third"}, note="구성원 third"),
        Step("tester", "POST", "/organizations/corp1/members", data={"loginId": "third"}, note="구성원 중복"),
        Step("other", "POST", "/organizations/corp1/members", data={"loginId": "admin"}, note="구성원 추가 권한 없음"),
        Step("tester", "POST", "/organizations/corp1/members", data={"loginId": "nobody"}, note="구성원 없는 사용자"),
        Step("tester", "POST", "/organizations/corp1/member/<<uid_third>>/edit", data={"id": "6"}, note="구성원 관리자로"),
        Step("other", "POST", "/organizations/corp1/member/<<uid_third>>/edit", data={"id": "7"}, note="구성원 역할 권한 없음"),
        Step("tester", "POST", "/organizations/corp1/member/<<uid_third>>/edit", data={"id": "99"}, note="구성원 알 수 없는 역할"),
        Step("tester", "POST", "/organizations/corp1/member/<<uid_third>>/edit", data={}, note="구성원 역할 없음"),
        Step("tester", "POST", "/organizations/corp1/member/<<uid_tester>>/edit", data={"id": "7"}, note="마지막 관리자 강등"),
        Step("tester", "POST", "/organizations/corp1/member/<<uid_third>>/edit", data={"id": "7"}, note="구성원 일반으로"),
        Step("tester", "POST", "/organizations/corp1/member/<<uid_admin>>/edit", data={"id": "7"}, note="구성원 아닌 사용자"),
        Step("tester", "GET", "/organizations/corp1/members", note="구성원 목록"),
    ]
    # 조직 소유 프로젝트와 한글 프로젝트
    s += [
        project("tester", "corp1", "app"),
        project("tester", "corp1", "secret", "PRIVATE"),
        project("tester", "corp1", "guarded", "PROTECTED"),
        project("other", "corp1", "byother"),
        project("third", "corp1", "bythird"),
        project("tester", "tester", "한글프로젝트"),
        project("tester", "corp1", "한글저장소", "PROTECTED"),
        project("tester", "한글조직", "한글앱"),
        project("tester", "tester", "한글 공백"),
        project("tester", "tester", "a.b-c_d"),
        project("tester", "tester", ".hidden"),
    ]
    # 내용
    for proj in ("corp1/app", "corp1/secret", "tester/한글프로젝트", "corp1/한글저장소"):
        s += [
            Step("tester", "POST", f"/{proj}/issues/latest", files=mp(title=f"{proj} 이슈", body="본문 @other", isDraft="false"), note=f"{proj} 이슈"),
            Step("tester", "POST", f"/{proj}/posts", data={"title": f"{proj} 글", "contents": "글 본문"}, note=f"{proj} 글"),
            Step("tester", "POST", f"/{proj}/milestones", data={"title": "M1", "contents": "마일스톤", "state": "OPEN", "dueDate": "2030-05-05"}, note=f"{proj} 마일스톤"),
        ]
    s += [
        Step("other", "POST", "/corp1/app/issue/1/comments", files=mp(contents="조직 구성원 댓글"), note="조직 구성원 댓글"),
        Step("third", "POST", "/corp1/secret/issue/1/comments", files=mp(contents="비공개 댓글"), note="비공개 구성원 댓글"),
        Step("admin", "POST", "/corp1/secret/issue/1/comments", files=mp(contents="관리자 댓글"), note="비공개 관리자 댓글"),
        Step("admin", "POST", "/corp1/guarded/issues/latest", files=mp(title="관리자 이슈", body="x", isDraft="false"), note="보호 관리자 이슈"),
    ]
    # 읽기
    reads = ["/corp1", "/corp1/app", "/corp1/app/issues", "/corp1/app/issue/1", "/corp1/app/posts", "/corp1/app/post/1", "/corp1/app/milestones", "/corp1/app/members",
             "/corp1/secret", "/corp1/secret/issues", "/corp1/secret/issue/1", "/corp1/guarded", "/corp1/guarded/issues", "/corp1/guarded/members", "/corp1/guarded/issue/1",
             "/corp1/byother", "/corp1/bythird", "/corp1/app/setting", "/corp1/secret/setting", "/corp1/secret/members",
             "/organizations/corp1", "/organizations/corp1/issues", "/organizations/corp1/issues?state=all", "/organizations/corp1/boards", "/organizations/corp1/pullrequests",
             "/organizations/corp1/members", "/organizations/corp1/settingform", "/organizations/corp1/deleteForm", "/organizations/corp1/search?keyword=이슈&searchType=issue",
             "/organizations/corp1/search?keyword=글&searchType=post", "/organizations/corp1/search?keyword=app&searchType=project", "/organizations/corp1/search?keyword=other&searchType=user",
             "/organizations/한글조직", "/organizations/%ED%95%9C%EA%B8%80%EC%A1%B0%EC%A7%81", "/한글조직/한글앱", "/%ED%95%9C%EA%B8%80%EC%A1%B0%EC%A7%81/%ED%95%9C%EA%B8%80%EC%95%B1",
             "/tester/한글프로젝트", "/tester/%ED%95%9C%EA%B8%80%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8", "/tester/한글프로젝트/issues", "/tester/한글프로젝트/issue/1", "/tester/한글프로젝트/posts",
             "/tester/한글프로젝트/code", "/tester/한글프로젝트/milestones", "/tester/한글프로젝트/settingform", "/tester/한글프로젝트/members", "/corp1/한글저장소", "/corp1/한글저장소/issues",
             "/tester/한글 공백", "/tester/%ED%95%9C%EA%B8%80%20%EA%B3%B5%EB%B0%B1", "/tester/a.b-c_d", "/tester/.hidden", "/tester/한글프로젝트.git",
             "/projects?filter=한글", "/projects?filter=corp1", "/orgs", "/orgs?filter=한글", "/orgs?filter=corp", "/tester", "/other", "/third", "/projects", "/"]
    s += sweep(reads, "읽기")
    # 프로젝트 목록 JSON과 사용자 화면의 조직 정보
    s += [Step(a, "GET", "/projects?filter=corp", headers=JSON, note=f"프로젝트 JSON [{a}]") for a in ACTORS]
    s += [Step(a, "GET", "/-_-api/v1/owners/corp1/projects/app/titleHeads", note=f"titleHeads [{a}]") for a in ACTORS]
    s += [Step(a, "GET", "/-_-api/v1/owners/corp1/projects/secret/exports", note=f"내보내기 비공개 [{a}]") for a in ACTORS]
    # 설정 바꾸기
    menus = dict(code="true", issue="true", pullRequest="true", review="true", milestone="true", board="true")
    s += [
        Step("tester", "GET", "/corp1/app/settingform", capture={"pid": ("body", r'name="id" value="(\d+)"')}, note="프로젝트 번호 읽기"),
        Step("tester", "POST", "/corp1/app/setting", files=mpf(id="<<pid>>", name="app", overview="설정 바꿈", projectScope="PRIVATE", isUsingReviewerCount="false", defaultReviewerCount="1", **menus),
             note="공개를 비공개로"),
        Step("other", "POST", "/corp1/app/setting", files=mpf(id="<<pid>>", name="app", overview="권한 없음", projectScope="PUBLIC", isUsingReviewerCount="false", defaultReviewerCount="1", **menus),
             note="설정 권한 없음"),
    ]
    s += sweep(["/corp1/app", "/corp1/app/issues", "/organizations/corp1", "/organizations/corp1/issues", "/projects?filter=corp", "/orgs?filter=corp"], "비공개로 바꾼 뒤")
    # 구성원 정리
    s += [
        Step("third", "DELETE", "/organizations/corp1/member/leave", note="조직 탈퇴"),
        Step("third", "DELETE", "/organizations/corp1/member/leave", note="조직 탈퇴 다시"),
        Step("tester", "DELETE", "/organizations/corp1/member/<<uid_other>>/delete", note="구성원 삭제"),
    ]
    s += sweep(["/corp1/secret", "/corp1/byother", "/organizations/corp1", "/organizations/corp1/members", "/organizations/corp1/issues"], "구성원 정리 뒤")
    # 웹훅과 알림 메일: 한글 이름 프로젝트의 주소가 어떻게 실리는지 본다(실행 뒤 hookcompare.py, mailcompare.py로 대조)
    for proj in ("tester/한글프로젝트", "corp1/한글저장소"):
        for kind, path in (("SIMPLE", "simple"), ("DETAIL_SLACK", "slack"), ("JSON", "json")):
            s.append(Step("tester", "POST", f"/{proj}/webhooks", data={"payloadUrl": f"http://127.0.0.1:<<sinkport>>/{path}", "secret": "s3cret", "webhookType": kind, "gitPush": "on"}, note=f"웹훅 {proj} {kind}"))
        s += [
            Step("tester", "POST", f"/{proj}/members", data={"loginId": "other"}, note=f"{proj} 구성원 other"),
            Step("tester", "POST", f"/{proj}/issues/latest", files=mp(title="알림 이슈", body="본문 @other", isDraft="false", assigneeLoginId="other"), note=f"{proj} 알림 이슈"),
            Step("other", "POST", f"/{proj}/issue/2/comments", files=mp(contents="알림 댓글"), note=f"{proj} 알림 댓글"),
            Step("tester", "POST", f"/{proj}/posts", files=mp(title="알림 글", body="글 본문", issueTemplate="", branch="", path="", lineEnding="UNDEFINED"), note=f"{proj} 알림 글"),
            Step("other", "POST", f"/{proj}/post/2/comment", files=mp(contents="글 댓글"), note=f"{proj} 글 댓글"),
            Step("tester", "POST", f"/{proj}/issue/2/comments", files=mp(contents="닫습니다", withStateTransition="close"), note=f"{proj} 이슈 닫기"),
        ]
    return s
