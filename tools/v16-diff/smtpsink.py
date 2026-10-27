#!/usr/bin/env python3
"""두 서버가 보내는 알림 메일을 받아 JSON 줄로 쌓는 아주 작은 SMTP 수신기이다. 인증은 모두 통과시키고 암호화는 제공하지 않는다.
  smtpsink.py <포트> <출력 파일>        예: smtpsink.py 19225 mails-v16.jsonl
메일 한 통마다 {"mail_from", "rcpt_to": [...], "data": "원문"} 한 줄을 추가한다."""
import asyncio
import json
import sys


async def handle(reader, writer, out):
    async def send(line):
        writer.write((line + "\r\n").encode())
        await writer.drain()

    await send("220 sink ESMTP")
    mail_from, rcpts = None, []
    while True:
        raw = await reader.readline()
        if not raw:
            break
        line = raw.decode("latin-1").rstrip("\r\n")
        cmd = line.upper()
        if cmd.startswith(("EHLO", "HELO")):
            writer.write(b"250-sink\r\n250-AUTH PLAIN LOGIN\r\n250-8BITMIME\r\n250 SIZE 52428800\r\n")
            await writer.drain()
        elif cmd.startswith("AUTH"):
            if "PLAIN" in cmd and len(line.split()) > 2:
                await send("235 ok")
            elif "PLAIN" in cmd:
                await send("334 ")
                await reader.readline()
                await send("235 ok")
            else:
                await send("334 VXNlcm5hbWU6")
                await reader.readline()
                await send("334 UGFzc3dvcmQ6")
                await reader.readline()
                await send("235 ok")
        elif cmd.startswith("MAIL FROM"):
            mail_from = line[line.index(":") + 1:].strip()
            rcpts = []
            await send("250 ok")
        elif cmd.startswith("RCPT TO"):
            rcpts.append(line[line.index(":") + 1:].strip())
            await send("250 ok")
        elif cmd == "DATA":
            await send("354 end with <CRLF>.<CRLF>")
            chunks = []
            while True:
                data_line = await reader.readline()
                if not data_line or data_line in (b".\r\n", b".\n"):
                    break
                if data_line.startswith(b".."):
                    data_line = data_line[1:]
                chunks.append(data_line)
            body = b"".join(chunks)
            with open(out, "a", encoding="utf-8") as f:
                f.write(json.dumps({"mail_from": mail_from, "rcpt_to": rcpts, "data": body.decode("utf-8", errors="surrogateescape").encode("utf-8", errors="replace").decode("utf-8")}, ensure_ascii=False) + "\n")
            await send("250 queued")
        elif cmd.startswith("RSET"):
            mail_from, rcpts = None, []
            await send("250 ok")
        elif cmd.startswith("NOOP"):
            await send("250 ok")
        elif cmd.startswith("QUIT"):
            await send("221 bye")
            break
        else:
            await send("250 ok")
    writer.close()


async def main(port, out):
    server = await asyncio.start_server(lambda r, w: handle(r, w, out), "127.0.0.1", port)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    asyncio.run(main(int(sys.argv[1]), sys.argv[2]))
