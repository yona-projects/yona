#!/usr/bin/env python3
"""두 가짜 IMAP 서버(v1.6: 143, v2: 19243)의 받은 편지함에 같은 시험 메일을 넣는다(메일 수신 대조용).
사용: imapmail.py   (imap.sh start 뒤 servers.sh restore data2 로 서버를 다시 띄운 상태에서 실행한다)
메시지 번호와 날짜는 고정해 두 서버가 같은 결과를 내게 한다. 처리를 기다린 뒤 `harness.py run imap1`과 `mailcompare.py`로 대조한다."""
import email.utils
import imaplib
import subprocess
import sys
import time
from email.header import Header
from email.message import EmailMessage
from email.mime.application import MIMEApplication
from email.mime.image import MIMEImage
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

DOMAIN = "example.com"
SITE = f"yona-in@{DOMAIN}"
PNG = b"\x89PNG\r\n\x1a\n" + bytes(range(64))
HEAD = subprocess.run(["git", "-C", "/home/jiho/yona-convert/yona-diff-work/git-tester-proj1-base", "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
counter = [0]


def stamp(msg, sender, to, subject, mid=None, cc=None, extra=None):
    counter[0] += 1
    msg["From"] = sender
    msg["To"] = to
    if cc:
        msg["Cc"] = cc
    msg["Subject"] = subject
    msg["Message-ID"] = mid or f"<imap-test-{counter[0]}@{DOMAIN}>"
    msg["Date"] = email.utils.format_datetime(email.utils.parsedate_to_datetime("Mon, 02 Mar 2026 09:%02d:00 +0900" % counter[0]))
    for k, v in (extra or {}).items():
        msg[k] = v
    return msg


def plain(sender, to, subject, body, **kw):
    return stamp(MIMEText(body, "plain", "utf-8"), sender, to, subject, **kw)


def build():
    t, o, th = f"tester@{DOMAIN}", f"other@{DOMAIN}", f"third@{DOMAIN}"
    proj = lambda p: f"yona-in+{p}@{DOMAIN}"  # noqa: E731
    msgs = []
    # 새 이슈
    msgs.append(plain(t, proj("tester/proj1"), "[메일] 평범한 새 이슈", "본문입니다.\n\n둘째 줄 @other 확인 부탁, #1 참고"))
    html = MIMEText("<html><body><h1>제목</h1><p><b>굵게</b> 그리고 <a href='http://example.org'>링크</a></p><ul><li>하나</li><li>둘</li></ul></body></html>", "html", "utf-8")
    msgs.append(stamp(html, o, proj("tester/proj1"), "HTML 전용 이슈"))
    alt = MIMEMultipart("alternative")
    alt.attach(MIMEText("일반 텍스트 부분", "plain", "utf-8"))
    alt.attach(MIMEText("<p>HTML <i>부분</i></p>", "html", "utf-8"))
    msgs.append(stamp(alt, t, proj("tester/proj1"), "대체 본문 이슈"))
    mixed = MIMEMultipart("mixed")
    mixed.attach(MIMEText("첨부가 있는 본문", "plain", "utf-8"))
    att = MIMEApplication("첨부 파일 내용\n".encode(), Name="메모.txt")
    att.add_header("Content-Disposition", "attachment", filename=("utf-8", "", "메모 파일.txt"))
    mixed.attach(att)
    img = MIMEImage(PNG, "png")
    img.add_header("Content-Disposition", "attachment", filename="그림.png")
    mixed.attach(img)
    msgs.append(stamp(mixed, t, proj("tester/proj1"), "첨부 파일이 있는 이슈"))
    inline = MIMEMultipart("related")
    inline.attach(MIMEText("<p>인라인 그림 <img src='cid:pic1'></p>", "html", "utf-8"))
    pic = MIMEImage(PNG, "png")
    pic.add_header("Content-ID", "<pic1>")
    pic.add_header("Content-Disposition", "inline", filename="inline.png")
    inline.attach(pic)
    msgs.append(stamp(inline, o, proj("tester/proj1"), "인라인 이미지 이슈"))
    b64 = EmailMessage()
    b64.set_content("베이스64로 보낸 한글 본문입니다.\n둘째 줄", cte="base64")
    msgs.append(stamp(b64, t, proj("tester/proj1"), str(Header("RFC2047 한글 제목 이슈", "utf-8"))))
    qp = EmailMessage()
    qp.set_content("quoted-printable 한글 본문 =로 끝나는 줄=\n", cte="quoted-printable")
    msgs.append(stamp(qp, o, proj("tester/proj1"), "QP 본문"))
    msgs.append(plain(t, proj("tester/proj1"), "", "제목 없는 이슈"))
    msgs.append(plain(t, proj("tester/proj1"), "본문 없는 이슈", ""))
    msgs.append(plain(t, proj("tester/proj1"), "긴 제목 " + "가나다라마바사" * 40, "긴 제목 이슈"))
    msgs.append(plain(t, proj("tester/proj1"), "말머리 [버그] 이슈", "말머리가 있는 제목"))
    msgs.append(plain(t, f"{proj('tester/proj1')}, {proj('tester/priv1')}", "두 프로젝트에 한 번에", "여러 수신 주소"))
    msgs.append(plain(t, proj("tester/proj1"), "참조 수신", "Cc로 다른 프로젝트", cc=proj("tester/prot1")))
    msgs.append(plain(t, proj("tester/한글프로젝트"), "한글 프로젝트 이슈", "한글 이름 프로젝트로"))
    # 답장(댓글)
    msgs.append(plain(o, f"yona-in+tester/proj1/issue_post/1@{DOMAIN}", "Re: [proj1] 첫 이슈 (#1)", "메일로 단 댓글입니다.\n\n> 인용된 줄\n\n--\n서명"))
    msgs.append(plain(t, f"yona-in+tester/proj1/issue_post/1@{DOMAIN}", "Re: 두 번째 댓글", "또 하나의 댓글 @other"))
    msgs.append(plain(o, f"yona-in+tester/proj1/board_post/1@{DOMAIN}", "Re: 글", "글에 다는 댓글"))
    msgs.append(plain(o, f"yona-in+tester/proj1/commit/{HEAD}@{DOMAIN}", "Re: 커밋", "커밋에 다는 댓글"))
    msgs.append(plain(o, f"yona-in+tester/proj1/issue_post/9999@{DOMAIN}", "Re: 없는 이슈", "없는 이슈에 댓글"))
    msgs.append(plain(o, f"yona-in+tester/proj1/nothing/1@{DOMAIN}", "Re: 알 수 없는 종류", "알 수 없는 종류"))
    # 거절되는 경우
    msgs.append(plain(f"nobody@{DOMAIN}", proj("tester/proj1"), "등록되지 않은 보낸 사람", "건너뛴다"))
    msgs.append(plain(th, proj("tester/priv1"), "권한 없는 프로젝트", "읽을 수 없는 비공개 프로젝트"))
    msgs.append(plain(t, proj("tester/없는프로젝트"), "없는 프로젝트", "없는 프로젝트로"))
    msgs.append(plain(t, f"yona-in@{DOMAIN}", "프로젝트 주소 없음", "세부 주소가 없다"))
    msgs.append(plain(t, proj("tester/proj1"), "자동 응답", "부재중", extra={"Auto-Submitted": "auto-replied"}))
    msgs.append(plain(t, proj("tester/proj1"), "나베르 부재", "부재중", extra={"X-Naver-Absent": "yes"}))
    msgs.append(plain(t, proj("tester/proj1"), "같은 메시지 번호 처음", "처음", mid="<same-id@example.com>"))
    msgs.append(plain(t, proj("tester/proj1"), "같은 메시지 번호 다시", "다시", mid="<same-id@example.com>"))
    msgs.append(plain(f"Tester <TESTER@{DOMAIN}>", proj("tester/proj1"), "대문자 보낸 사람", "대소문자가 다른 주소"))
    msgs.append(plain(f"=?utf-8?b?7YWM7Iqk7YSw?= <{t}>", proj("tester/proj1"), "표시 이름이 인코딩된 보낸 사람", "표시 이름"))
    return msgs


def main():
    msgs = build()
    for label, host, port in (("v1.6", "127.0.0.1", 143), ("v2", "127.0.0.1", 19243)):
        m = imaplib.IMAP4(host, port)
        m.login(SITE, "pw")
        for msg in msgs:
            m.append("INBOX", "", imaplib.Time2Internaldate(time.time()), msg.as_bytes())
        m.logout()
        print(f"{label}: {len(msgs)}통 넣음")


if __name__ == "__main__":
    main()
