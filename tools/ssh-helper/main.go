// yona-ssh는 Yona의 시스템 sshd 연동(추가 개발 항목 X-3b, decisions.md D-67, D-73)을 돕는 작은 실행 파일이다. 셸 훅(tools/ssh/yona-ssh-auth.sh)과 `bash`, `curl`, `socat`을 대신해
// 같은 일을 셸 없이 한다. 하위 명령은 둘이다.
//
//	yona-ssh auth <키 종류> <키 본문>
//	    sshd의 AuthorizedKeysCommand로 등록한다(`AuthorizedKeysCommand /usr/local/bin/yona-ssh auth %t %k`). Yona의 내부 인증 주소에 키를 묻고,
//	    등록된 키면 authorized_keys 줄을 인쇄한다. 그 줄의 강제 명령이 아래 `relay`이다. 모르는 키이거나 오류면 아무것도 인쇄하지 않고 0으로 끝난다(접근을 잘못
//	    허용하지 않고 조용히 실패한다).
//
//	yona-ssh relay --socket <경로> <인증 표>
//	    강제 명령으로 실행된다. Yona의 중계 소켓에 연결해 세 줄(인증 표, 클라이언트 주소, 클라이언트가 요청한 명령)을 보내고, 그 뒤는 표준 입출력을 소켓에 이어 붙이는
//	    바이트 중계이다. 원본 명령이 없는 접속(`ssh -T`)은 셸을 주지 않고 신원 확인 문구만 보인다.
//
// 표준 라이브러리만 쓰므로 Linux, macOS, Windows에서 같은 소스로 빌드된다: `go build -o yona-ssh .`
package main

import (
	"fmt"
	"io"
	"os"
)

func main() {
	executable, err := os.Executable()
	if err != nil {
		executable = os.Args[0]
	}
	os.Exit(run(os.Args[1:], os.Getenv, os.Stdin, os.Stdout, os.Stderr, executable))
}

// run은 하위 명령을 실행하고 종료 코드를 돌려준다. 입출력과 환경을 인자로 받아 시험할 수 있다.
func run(args []string, getenv func(string) string, stdin io.Reader, stdout, stderr io.Writer, executable string) int {
	if len(args) == 0 {
		fmt.Fprintln(stderr, "usage: yona-ssh auth <type> <key> | yona-ssh relay --socket <path> <ticket>")
		return 2
	}
	switch args[0] {
	case "auth":
		return runAuth(args[1:], getenv, stdout, stderr, executable)
	case "relay":
		return runRelay(args[1:], getenv, stdin, stdout, stderr)
	default:
		fmt.Fprintf(stderr, "yona-ssh: unknown command %q\n", args[0])
		return 2
	}
}
