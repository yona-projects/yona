"""첨부 파일: 올리기(여러 이름과 형식, 중복, 응답 형식), 받기(조건부 요청, 범위, 내려받기), 지우기, 목록, 이슈와 댓글과 글에 붙이기, 비공개 프로젝트의 읽기 권한.
data2 상태에서 한 번만 실행한다. 번호는 응답 본문에서 기억한다."""
import unicodedata

from harness import Step
from data1 import login, mp

ACTORS = ["anon", "tester", "other", "third", "admin"]
ID = r'"id":"?(\d+)'
JSON = {"Accept": "application/json"}
HTML = {"Accept": "text/html"}
PNG = b"\x89PNG\r\n\x1a\n" + bytes(range(256)) * 4


def up(actor, name, content, mime, key=None, note=None, headers=None, data=None):
    cap = {key: ("body", ID)} if key else {}
    return Step(actor, "POST", "/files", files={"filePath": (name, content, mime)}, data=data or {}, capture=cap, headers=headers or {}, note=note or f"올리기 {name[:30]} [{actor}]")


def steps():
    s = []
    s += [login("tester"), login("other"), login("third"), login("admin", "admin1234")]
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    # 올리기: 이름과 형식
    s += [
        up("tester", "a.txt", b"attachment text", "text/plain", "t1"),
        up("tester", "a.txt", b"attachment text", "text/plain", "t1dup", note="같은 이름 같은 내용 다시"),
        up("tester", "b.txt", b"attachment text", "text/plain", "t1name", note="다른 이름 같은 내용"),
        up("other", "a.txt", b"attachment text", "text/plain", "o1", note="다른 사용자 같은 내용"),
        up("tester", "한글 파일.png", PNG, "image/png", "kpng"),
        up("tester", unicodedata.normalize("NFD", "분해된 이름.txt"), b"nfd", "text/plain", "nfd"),
        up("tester", "공백 이 있는 이름 (1).txt", b"space", "text/plain", "space"),
        up("tester", 'quote"semi;colon.txt', b"quote", "text/plain", "quote"),
        up("tester", "emoji-😀.txt", b"emoji", "text/plain", "emoji"),
        up("tester", "x" * 300 + ".txt", b"long", "text/plain", "long"),
        up("tester", "../evil.txt", b"evil", "text/plain", "evil"),
        up("tester", "dir\\back.txt", b"back", "text/plain", "back"),
        up("tester", "README", b"noext", "application/octet-stream", "noext"),
        up("tester", "UPPER.PNG", PNG, "image/png", "upper"),
        up("tester", "page.html", b"<html><script>alert(1)</script></html>", "text/html", "html"),
        up("tester", "pic.svg", b"<svg xmlns='http://www.w3.org/2000/svg'><script>1</script></svg>", "image/svg+xml", "svg"),
        up("tester", "doc.pdf", b"%PDF-1.4\n%fake", "application/pdf", "pdf"),
        up("tester", "empty.txt", b"", "text/plain", "empty"),
        up("tester", "lie.txt", PNG, "image/png", "lie", note="확장자와 형식이 다른 것"),
        up("tester", "nomime.bin", b"\x00\x01\x02", "", "nomime", note="형식 없음"),
        up("tester", "big.bin", bytes(range(256)) * 12000, "application/octet-stream", "big", note="3MB"),
        up("tester", "json.json", b'{"a":1}', "application/json", "json"),
        up("tester", "a.txt", b"accept json", "text/plain", note="Accept json", headers=JSON),
        up("tester", "a.txt", b"accept html", "text/plain", note="Accept html", headers=HTML),
        up("tester", "a.txt", b"accept any", "text/plain", note="Accept 모두", headers={"Accept": "*/*"}),
        up("tester", "a.txt", b"accept xml", "text/plain", note="Accept xml", headers={"Accept": "application/xml"}),
        up("tester", "a.txt", b"author mail", "text/plain", "byemail", note="작성자 이메일 지정", data={"authorEmail": "other@example.com"}),
        up("tester", "a.txt", b"author login", "text/plain", "bylogin", note="작성자 로그인 아이디 지정", data={"authorLoginId": "other"}),
        up("tester", "a.txt", b"author none", "text/plain", note="없는 작성자 지정", data={"authorEmail": "nobody@example.com", "authorLoginId": "nobody"}),
        up("anon", "a.txt", b"anon", "text/plain", note="익명"),
        Step("tester", "POST", "/files", note="파일 부분 없음"),
        Step("tester", "POST", "/files", files={"other": ("a.txt", b"x", "text/plain")}, note="다른 부분 이름"),
        Step("tester", "POST", "/files", data={"filePath": "text"}, note="파일 아닌 값"),
    ]
    # 받기
    for key in ("t1", "kpng", "nfd", "space", "quote", "emoji", "long", "evil", "back", "noext", "upper", "html", "svg", "pdf", "empty", "lie", "nomime", "json"):
        s += [
            Step("tester", "GET", f"/files/<<{key}>>", note=f"받기 {key}"),
            Step("tester", "GET", f"/files/<<{key}>>?action=download", note=f"내려받기 {key}"),
        ]
    s += [
        Step("anon", "GET", "/files/<<t1>>", note="받기 익명"),
        Step("other", "GET", "/files/<<t1>>", note="받기 다른 사용자"),
        Step("tester", "GET", "/files/<<t1>>/", note="받기 끝 슬래시"),
        Step("tester", "GET", "/files/<<t1>>?action=other", note="받기 다른 action"),
        Step("tester", "GET", "/files/<<t1>>?action=download&action=inline", note="받기 action 둘"),
        Step("tester", "GET", "/files/<<big>>", headers={"Range": "bytes=0-99"}, note="범위 요청"),
        Step("tester", "GET", "/files/<<big>>", headers={"Range": "bytes=100-"}, note="범위 요청 뒤"),
        Step("tester", "GET", "/files/<<big>>", headers={"Range": "bytes=-50"}, note="범위 요청 끝"),
        Step("tester", "GET", "/files/<<big>>", headers={"Range": "bytes=99999999-"}, note="범위 요청 넘음"),
        Step("tester", "GET", "/files/<<big>>", headers={"Range": "nonsense"}, note="범위 요청 틀림"),
        Step("tester", "GET", "/files/<<t1>>", headers={"If-None-Match": '"nomatch"'}, note="조건부 다른 값"),
        Step("tester", "GET", "/files/<<t1>>", headers={"If-None-Match": "*"}, note="조건부 별표"),
        Step("tester", "GET", "/files/<<t1>>", headers={"If-Modified-Since": "Wed, 21 Oct 2099 07:28:00 GMT"}, note="수정 시각 조건"),
        Step("tester", "GET", "/files/0", note="받기 0"),
        Step("tester", "GET", "/files/999999", note="받기 없는 번호"),
        Step("tester", "GET", "/files/abc", note="받기 문자 번호"),
        Step("tester", "GET", "/files/-1", note="받기 음수"),
        Step("tester", "GET", "/files/99999999999999999999", note="받기 큰 번호"),
        Step("tester", "HEAD", "/files/<<t1>>", note="HEAD"),
        Step("tester", "OPTIONS", "/files/<<t1>>", note="OPTIONS"),
        Step("tester", "PUT", "/files/<<t1>>", note="PUT"),
        Step("tester", "PATCH", "/files", note="PATCH 목록"),
    ]
    # 목록
    s += [
        Step("tester", "GET", "/files", note="목록"),
        Step("tester", "GET", "/files?containerType=USER&containerId=<<uid_tester>>", note="목록 사용자 컨테이너"),
        Step("anon", "GET", "/files?containerType=USER&containerId=<<uid_tester>>", note="목록 익명"),
        Step("other", "GET", "/files?containerType=USER&containerId=<<uid_tester>>", note="목록 다른 사용자"),
        Step("tester", "GET", "/files?containerType=NOPE&containerId=1", note="목록 알 수 없는 종류"),
        Step("tester", "GET", "/files?containerType=ISSUE_POST&containerId=abc", note="목록 문자 번호"),
        Step("tester", "GET", "/files?containerType=ISSUE_POST", note="목록 번호 없음"),
        Step("tester", "GET", "/files?containerId=1", note="목록 종류 없음"),
        Step("tester", "GET", "/user/files", note="내 파일"),
        Step("tester", "GET", "/user/files?pageNum=2", note="내 파일 2쪽"),
        Step("tester", "GET", "/user/files?filter=a.txt", note="내 파일 검색"),
        Step("tester", "GET", "/user/files?filter=한글", note="내 파일 한글 검색"),
        Step("anon", "GET", "/user/files", note="내 파일 익명"),
        Step("other", "GET", "/user/files", note="내 파일 다른 사용자"),
    ]
    # 이슈, 댓글, 글에 붙이기와 읽기 권한
    s += [
        Step("tester", "POST", "/tester/proj1/issues/latest", files=mp(title="첨부 이슈", body="첨부 본문", isDraft="false", temporaryUploadFiles="<<t1name>>"), note="이슈에 첨부"),
        Step("tester", "POST", "/tester/priv1/issues/latest", files=mp(title="비공개 첨부 이슈", body="비공개 본문", isDraft="false", temporaryUploadFiles="<<kpng>>"), note="비공개 이슈에 첨부"),
        Step("tester", "POST", "/tester/proj1/issues/latest", files=mp(title="없는 첨부 이슈", body="x", isDraft="false", temporaryUploadFiles="99999"), note="없는 첨부 번호"),
        Step("tester", "POST", "/tester/proj1/issues/latest", files=mp(title="남의 첨부 이슈", body="x", isDraft="false", temporaryUploadFiles="<<o1>>"), note="남의 첨부 번호"),
        Step("tester", "GET", "/tester/proj1/issues", capture={"i_att": ("body", r'href="/tester/proj1/issue/(\d+)"[^>]*>\s*(?:<[^>]+>\s*)*첨부 이슈')}, note="첨부 이슈 번호 찾기"),
    ]
    for a in ACTORS:
        s += [
            Step(a, "GET", "/files/<<t1name>>", note=f"공개 이슈에 붙은 첨부 받기 [{a}]"),
            Step(a, "GET", "/files/<<kpng>>", note=f"비공개 이슈에 붙은 첨부 받기 [{a}]"),
            Step(a, "GET", "/files/<<t1>>", note=f"사용자 첨부 받기 [{a}]"),
            Step(a, "GET", "/files?containerType=ISSUE_POST&containerId=<<i_att>>", note=f"이슈 첨부 목록 [{a}]"),
        ]
    s += [
        Step("tester", "GET", "/tester/proj1/issue/<<i_att>>", note="첨부 이슈 보기"),
        Step("tester", "POST", "/tester/proj1/issue/<<i_att>>/comments", files=mp(contents="댓글 첨부", temporaryUploadFiles="<<space>>"), note="댓글에 첨부"),
        Step("tester", "GET", "/tester/proj1/issue/<<i_att>>", note="댓글 첨부 보기"),
        Step("tester", "POST", "/tester/proj1/posts", files=mp(title="첨부 글", body="글 첨부", issueTemplate="", branch="", path="", lineEnding="UNDEFINED", temporaryUploadFiles="<<emoji>>"), note="글에 첨부"),
        Step("tester", "GET", "/files?containerType=ISSUE_POST&containerId=<<i_att>>", note="이슈 첨부 목록 뒤"),
    ]
    # 지우기
    s += [
        Step("other", "POST", "/files/<<t1dup>>", files={"_method": (None, "delete")}, note="지우기 남의 것"),
        Step("anon", "POST", "/files/<<t1dup>>", files={"_method": (None, "delete")}, note="지우기 익명"),
        Step("tester", "POST", "/files/<<t1dup>>", files={"_method": (None, "post")}, note="지우기 _method 틀림"),
        Step("tester", "POST", "/files/<<t1dup>>", files={"x": (None, "y")}, note="지우기 _method 없음"),
        Step("tester", "POST", "/files/<<t1dup>>", data={"_method": "delete"}, note="지우기 urlencoded"),
        Step("tester", "POST", "/files/<<t1dup>>", files={"_method": (None, "DELETE")}, note="지우기 대문자"),
        Step("tester", "POST", "/files/<<t1dup>>", files={"_method": (None, "delete")}, note="지우기 다시"),
        Step("tester", "POST", "/files/<<t1dup>>/", files={"_method": (None, "delete")}, note="지우기 끝 슬래시"),
        Step("admin", "POST", "/files/<<empty>>", files={"_method": (None, "delete")}, note="지우기 관리자"),
        Step("tester", "POST", "/files/<<t1name>>", files={"_method": (None, "delete")}, note="지우기 이슈에 붙은 것"),
        Step("tester", "POST", "/files/99999", files={"_method": (None, "delete")}, note="지우기 없는 것"),
        Step("tester", "DELETE", "/files/<<nomime>>", note="DELETE 메서드"),
        Step("tester", "GET", "/files/<<t1dup>>", note="지운 뒤 받기"),
        Step("tester", "GET", "/files/<<t1>>", note="같은 내용 다른 첨부 받기"),
    ]
    return s
