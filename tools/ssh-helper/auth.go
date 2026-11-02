package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"regexp"
	"strings"
	"time"
)

// authTimeout은 Yona 서버에 묻는 시간 제한이다(시험에서 줄인다).
var authTimeout = 5 * time.Second

var (
	keyTypePattern   = regexp.MustCompile(`^[A-Za-z0-9@._-]+$`)
	keyBlobPattern   = regexp.MustCompile(`^[A-Za-z0-9+/=]+$`)
	principalPattern = regexp.MustCompile(`^[A-Za-z0-9._-]+$`)
	// 강제 명령에 넣는 경로는 셸이 해석할 수 있는 문자(공백, 따옴표, $, 역슬래시 등)가 없어야 한다. sshd가 강제 명령을 계정의 셸로 실행하기 때문이다.
	safePathPattern = regexp.MustCompile(`^[A-Za-z0-9_./+:\\-]+$`)
)

// runAuth는 `auth` 하위 명령이다. 어떤 실패도 아무것도 인쇄하지 않고 0으로 끝나 sshd가 인증을 거부하게 한다.
func runAuth(args []string, getenv func(string) string, stdout, stderr io.Writer, executable string) int {
	if len(args) < 2 || args[0] == "" || args[1] == "" {
		return 0
	}
	keyType, keyBlob := args[0], args[1]
	if !keyTypePattern.MatchString(keyType) || !keyBlobPattern.MatchString(keyBlob) {
		return 0
	}
	conf := loadConfig(getenv)
	if !safePathPattern.MatchString(executable) || !safePathPattern.MatchString(conf.Socket) {
		fmt.Fprintln(stderr, "yona-ssh: the executable path or the relay socket path has characters that are not allowed in a forced command")
		return 0
	}
	secret, err := os.ReadFile(conf.SecretFile)
	if err != nil {
		return 0
	}
	principal := ask(conf.URL, strings.TrimSpace(string(secret)), keyType+" "+keyBlob)
	if principal == "" {
		return 0
	}
	// authorized_keys의 command="..." 값 안에는 큰따옴표가 없어야 한다(경로와 표의 문자 집합을 위에서 제한했다)
	fmt.Fprintf(stdout, "command=\"%s relay --socket %s %s\",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty %s %s\n", executable, conf.Socket, principal, keyType, keyBlob)
	return 0
}

// ask는 Yona의 내부 인증 주소에 공개키를 묻고 서명한 인증 표를 돌려준다. 모르는 키이거나 오류이면 빈 문자열이다.
func ask(baseURL, secret, publicKey string) string {
	body, _ := json.Marshal(map[string]string{"publicKey": publicKey})
	request, err := http.NewRequest(http.MethodPost, strings.TrimRight(baseURL, "/")+"/internal/ssh/authenticate", bytes.NewReader(body))
	if err != nil {
		return ""
	}
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("X-Yona-Internal-Secret", secret)
	response, err := (&http.Client{Timeout: authTimeout}).Do(request)
	if err != nil {
		return ""
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return ""
	}
	var reply struct {
		Principal string `json:"principal"`
	}
	if err := json.NewDecoder(io.LimitReader(response.Body, 4096)).Decode(&reply); err != nil {
		return ""
	}
	if !principalPattern.MatchString(reply.Principal) {
		return ""
	}
	return reply.Principal
}
