"""메일로 받은 글(imapmail.py가 넣은 시험 메일)이 이슈, 댓글로 만들어진 결과를 읽는다. imap.sh start, servers.sh restore data2, imapmail.py를 실행하고 처리를 기다린 뒤 돌린다.
이슈 목록과 각 이슈, 글, 댓글이 달린 이슈 1, 비공개 프로젝트, 한글 프로젝트, 첨부 목록을 본다. 오류 회신 메일은 mailcompare.py로 대조한다."""
from harness import Step
from data1 import login

ACTORS = ["anon", "tester", "other"]


def steps():
    s = [login("tester"), login("other")]
    paths = ["/tester/proj1/issues?state=all", "/tester/proj1/issues?state=all&pageNum=2", "/tester/proj1/posts", "/tester/priv1/issues?state=all", "/tester/prot1/issues?state=all",
             "/tester/한글프로젝트/issues?state=all", "/tester/proj1/issue/1", "/tester/proj1/post/1", "/tester/proj1/commit/<<head>>", "/tester/proj1/commits", "/notifications", "/tester/proj1"]
    paths += [f"/tester/proj1/issue/{n}" for n in range(4, 24)]
    paths += [f"/files?containerType=ISSUE_POST&containerId=<<i{n}>>" for n in range(4, 24)]
    paths += ["/tester/priv1/issue/2", "/tester/priv1/issue/3", "/tester/prot1/issue/1", "/tester/한글프로젝트/issue/1", "/tester/한글프로젝트/issue/2"]
    # 파일 목록은 이슈의 내부 번호가 필요하므로 이슈 페이지에서 읽는다
    for n in range(4, 24):
        s.append(Step("tester", "GET", f"/tester/proj1/issue/{n}", capture={f"i{n}": ("body", r'data-attachments="[^"]*&quot;id&quot;:&quot;(\d+)')}, note=f"이슈 {n}에서 번호 읽기"))
    for p in paths:
        for a in ACTORS:
            s.append(Step(a, "GET", p, note=f"{p} [{a}]"))
    return s
