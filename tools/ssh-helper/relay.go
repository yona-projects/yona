package main

import (
	"fmt"
	"io"
	"net"
	"strings"
)

// greeting은 원본 명령이 없는 접속(`ssh -T`)에 보이는 문구이다(GitHub와 같은 방식).
const greeting = "Hi! Yona SSH authentication succeeded. Interactive shell access is not provided."

// runRelay는 `relay` 하위 명령이다. 강제 명령으로 실행되어 Yona의 중계 소켓에 표준 입출력을 이어 붙인다.
func runRelay(args []string, getenv func(string) string, stdin io.Reader, stdout, stderr io.Writer) int {
	socket, ticket := "", ""
	for i := 0; i < len(args); i++ {
		switch {
		case args[i] == "--socket" && i+1 < len(args):
			socket = args[i+1]
			i++
		case ticket == "":
			ticket = args[i]
		}
	}
	if socket == "" || ticket == "" {
		fmt.Fprintln(stderr, "usage: yona-ssh relay --socket <path> <ticket>")
		return 2
	}
	command := getenv("SSH_ORIGINAL_COMMAND")
	if command == "" {
		fmt.Fprintln(stderr, greeting)
		return 1
	}
	// 소켓 프로토콜은 개행으로 줄을 가르므로 개행이 든 명령은 줄을 더 끼워 넣을 수 있다. 보내지 않고 거부한다.
	if strings.ContainsAny(command, "\r\n") {
		fmt.Fprintln(stderr, "yona-ssh: the command contains a line break")
		return 1
	}
	client := strings.Fields(getenv("SSH_CLIENT"))
	address := ""
	if len(client) > 0 {
		address = client[0]
	}

	conn, err := net.Dial("unix", socket)
	if err != nil {
		fmt.Fprintf(stderr, "yona-ssh: cannot connect to the relay socket %s: %v\n", socket, err)
		return 1
	}
	defer conn.Close()
	if _, err := fmt.Fprintf(conn, "%s\n%s\n%s\n", ticket, address, command); err != nil {
		fmt.Fprintf(stderr, "yona-ssh: cannot write to the relay socket: %v\n", err)
		return 1
	}
	// 클라이언트의 입력을 소켓으로 보낸다. 입력이 끝나면(EOF) 쓰기만 닫아 서버가 끝을 알게 한다.
	go func() {
		_, _ = io.Copy(conn, stdin)
		if closer, ok := conn.(interface{ CloseWrite() error }); ok {
			_ = closer.CloseWrite()
		}
	}()
	// 응답은 서버가 닫을 때까지 표준 출력으로 보낸다. 서버가 먼저 끝나면(거부 등) 입력을 기다리지 않고 바로 끝낸다.
	_, _ = io.Copy(stdout, conn)
	return 0
}
