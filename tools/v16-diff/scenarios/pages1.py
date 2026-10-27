"""데이터가 있는 상태(data1)에서 여러 사용자가 화면과 JSON을 읽는다. 읽기만 하므로 반복해서 실행할 수 있다."""
from harness import Step

ACTORS = ["anon", "admin", "tester", "other", "third"]


def project_paths(o, p):
    b = f"/{o}/{p}"
    return [
        b, f"{b}/issues", f"{b}/issues?state=closed", f"{b}/issues?state=all", f"{b}/issues?milestoneId=1", f"{b}/issues?assigneeId=2",
        f"{b}/issues?filter=첫", f"{b}/issues?orderBy=updatedDate&orderDir=asc", f"{b}/issues?format=json", f"{b}/issues?pageNum=2",
        f"{b}/issue/1", f"{b}/issue/2", f"{b}/issue/3", f"{b}/issue/99", f"{b}/issue/1/timeline", f"{b}/issue/1/editform",
        f"{b}/issueform", f"{b}/milestones", f"{b}/milestones?state=closed", f"{b}/milestones?state=all", f"{b}/milestone/1", f"{b}/milestone/2",
        f"{b}/milestone/99", f"{b}/newMilestoneForm", f"{b}/milestone/1/editform",
        f"{b}/posts", f"{b}/posts?filter=글", f"{b}/post/1", f"{b}/post/2", f"{b}/post/99", f"{b}/postform", f"{b}/post/1/editform",
        f"{b}/members", f"{b}/settingform", f"{b}/webhooks", f"{b}/watchers", f"{b}/labels", f"{b}/issue/labels", f"{b}/issue/labelsform",
        f"{b}/issue/labels.css", f"{b}/issue/label/categories", f"{b}/mentionList", f"{b}/mentionList?query=o", f"{b}/statistics",
        f"{b}/reviews", f"{b}/pullRequests", f"{b}/closedPullRequests", f"{b}/sentPullRequests", f"{b}/newPullRequestForm", f"{b}/newFork",
        f"{b}/code", f"{b}/commits", f"{b}/branches", f"{b}/deleteform", f"{b}/transfer", f"{b}/changeVCS", f"{b}/go", f"{b}/search?keyword=이슈",
    ]


def steps():
    s = []
    s.append(Step("admin", "POST", "/users/login", data={"loginIdOrEmail": "admin", "password": "admin1234"}, note="관리자 로그인"))
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    for actor in ACTORS:
        if actor != "anon":
            pw = "admin1234" if actor == "admin" else "pass1234"
            s.append(Step(actor, "POST", "/users/login", data={"loginIdOrEmail": actor, "password": pw}, note=f"{actor} 로그인"))
    for actor in ACTORS:
        paths = []
        for o, p in (("tester", "proj1"), ("tester", "priv1"), ("tester", "prot1")):
            paths += project_paths(o, p)
        paths += [
            "/", "/projects", "/projects?filter=proj", "/projects?pageNum=2", "/orgs", "/orgs?filter=org", "/organizations/org1", "/organizations/org1/issues",
            "/organizations/org1/boards", "/organizations/org1/pullrequests", "/organizations/org1/closedPullrequests", "/organizations/org1/members",
            "/organizations/org1/settingform", "/organizations/org1/deleteForm", "/organizations/org1/search?keyword=x", "/organizations/nope",
            "/tester", "/tester?daysAgo=7&selected=issues", "/tester?selected=projects", "/other", "/third", "/nobody", "/admin",
            "/user/issues", "/user/issues?state=closed", "/user/issues?format=json", "/user/sidebar", "/user/usermenuTabContentList",
            "/user/editform", "/user/editform/password", "/user/editform/notifications", "/user/editform/emails", "/user/editform/token",
            "/user/files", "/notification", "/notification?start=0", "/search?keyword=이슈", "/search?keyword=이슈&searchType=issue",
            "/search?keyword=proj&searchType=project", "/projectform", "/organizations/new", "/user/issues/new", "/user/issues/new/mine",
            "/sites/userList", "/sites/userList?query=te", "/sites/userList?state=LOCKED", "/sites/projectList", "/sites/postList", "/sites/issueList",
            "/sites/mail", "/sites/massmail", "/sites/data", "/sites/update", "/sites/diagnostic", "/sites/noAvatarUsers",
            "/-_-api/v1/hello", "/-_-api/v1/users?query=t", "/-_-api/v1/owners/tester/projects/proj1/titleHeads", "/-_-api/v1/favoriteProjects",
            "/labels", "/labels?query=b", "/categories", "/messages.js", "/_help", "/_UIKit", "/info/leave/tester/proj1",
            "/user/isUsed?name=tester", "/user/isUsed?name=zzz", "/user/isEmailExist?email=tester@example.com",
        ]
        s += [Step(actor, "GET", p) for p in paths]
    return s
