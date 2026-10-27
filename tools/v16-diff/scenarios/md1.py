"""마크다운 변환 비교: 여러 조각을 이슈 본문, 게시글 본문, 댓글, 코드 보기(README)에 올리고 화면을 읽는다. data2 상태에서 한 번만 실행한다."""
from harness import Step
from data1 import mp

SNIPPETS = [
    ("제목들", "# 제목1\n## 제목2\n### 제목3 `코드`\n#### 4\n##### 5\n###### 6\n\n제목1\n=====\n\n제목2\n-----\n\n# 제목1\n\n# 한글 제목 & 특수 <b>\n"),
    ("강조", "**굵게** *기울임* ***둘다*** ~~취소~~ `인라인 코드` <u>밑줄</u> H~2~O x^2^\n\n줄바꿈  \n두번째줄\n\n세번째\n네번째\n"),
    ("목록", "- a\n- b\n  - b1\n  - b2\n    - b2a\n- c\n\n1. one\n2. two\n   1. two-a\n3. three\n\n- [ ] 할 일\n- [x] 한 일\n\n* 별표\n+ 더하기\n"),
    ("코드", "```java\npublic class A { int x = 1; }\n```\n\n```\n그냥 코드\n<b>태그</b>\n```\n\n    들여쓴 코드\n    두번째\n\n```python\ndef f():\n    return \"x\"\n```\n\n```javascript\nvar a = {b: 1};\n```\n\n```unknownlang\nabc\n```\n"),
    ("링크", "[링크](http://example.com) [상대](./a/b.md) [이슈](/tester/proj1/issue/1) <http://auto.example.com> http://bare.example.com/path?q=1&r=2\n\n![이미지](http://example.com/a.png) ![상대 이미지](./logo.png) [js](javascript:alert(1)) [mail](mailto:a@b.c)\n\n#1 이슈참조 @other 멘션 @nobody 없는멘션 tester/proj1#1 `#1` \\#1\n"),
    ("표", "| 가 | 나 | 다 |\n|---|:---:|--:|\n| 1 | 2 | 3 |\n| **굵게** | `코드` | [링크](http://x.y) |\n\n| a |\n|---|\n| b |\n"),
    ("인용과 구분선", "> 인용\n> 두번째\n>> 중첩\n\n---\n***\n___\n\n> - 목록\n> - 둘\n"),
    ("HTML", "<div class=\"x\" style=\"color:red\" onclick=\"alert(1)\">div</div>\n<script>alert(1)</script>\n<iframe src=\"http://example.com\"></iframe>\n<img src=\"x\" onerror=\"alert(1)\">\n<video controls src=\"a.mp4\"></video>\n<details><summary>요약</summary>본문</details>\n<!-- 주석 -->\n<span id=\"s\">span</span> <a href=\"http://x.y\" target=\"_blank\">a</a>\n<kbd>Ctrl</kbd> <mark>mark</mark>\n"),
    ("이모지와 특수문자", ":smile: :+1: :tada: &amp; &lt; &copy; © ™ \\* \\_ 한글 😀 \u200b\n\n* * *\n_ _ _\n\n각주[^1]\n\n[^1]: 각주 내용\n"),
    ("긴 줄과 혼합", "a" * 300 + "\n\n" + "한글 " * 80 + "\n\n1. 목록\n\n   문단\n\n   ```\n   코드\n   ```\n\n2. 다음\n"),
]


def steps():
    s = []
    for u in ("tester", "other", "admin"):
        pw = "admin1234" if u == "admin" else "pass1234"
        s.append(Step(u, "POST", "/users/login", data={"loginIdOrEmail": u, "password": pw}, note=f"{u} 로그인"))
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    for i, (name, body) in enumerate(SNIPPETS, 1):
        s.append(Step("tester", "POST", "/tester/proj1/issues/latest", files=mp(title=f"MD {name}", body=body, isDraft="false"), note=f"이슈 {name}"))
        s.append(Step("tester", "POST", "/tester/proj1/posts", files=mp(title=f"MD {name}", body=body, issueTemplate="", branch="", path="", lineEnding="UNDEFINED"), note=f"글 {name}"))
    n = len(SNIPPETS)
    for actor in ("anon", "tester", "other"):
        s += [Step(actor, "GET", f"/tester/proj1/issue/{4 + k}") for k in range(n)]
        s += [Step(actor, "GET", f"/tester/proj1/post/{3 + k}") for k in range(n)]
        s += [Step(actor, "GET", "/tester/proj1/issues"), Step(actor, "GET", "/tester/proj1/posts"), Step(actor, "GET", "/tester/proj1/issues?filter=MD")]
    # 댓글과 미리보기
    for k, (name, body) in enumerate(SNIPPETS):
        s.append(Step("other", "POST", f"/tester/proj1/issue/{4 + k}/comments", files=mp(contents=body), note=f"댓글 {name}"))
    s += [Step("tester", "GET", f"/tester/proj1/issue/{4 + k}") for k in range(n)]
    for name, body in SNIPPETS:
        s.append(Step("tester", "POST", "/markdown/tester/proj1", data={"body": body}, note=f"미리보기 {name}"))
    return s
