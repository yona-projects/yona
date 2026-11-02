package main

import (
	"bytes"
	"encoding/json"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// env는 시험용 환경 변수 조회 함수이다.
func env(values map[string]string) func(string) string {
	return func(name string) string { return values[name] }
}

func writeFile(t *testing.T, dir, name, content string) string {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}

// authFixture는 가짜 Yona 내부 인증 주소와 설정을 마련한다.
type authFixture struct {
	server   *httptest.Server
	received *http.Request
	body     map[string]string
	env      map[string]string
}

func newAuthFixture(t *testing.T, status int, reply string) *authFixture {
	t.Helper()
	f := &authFixture{}
	f.server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		f.received = r
		_ = json.NewDecoder(r.Body).Decode(&f.body)
		w.WriteHeader(status)
		_, _ = io.WriteString(w, reply)
	}))
	t.Cleanup(f.server.Close)
	dir := t.TempDir()
	f.env = map[string]string{
		"YONA_INTERNAL_URL":     f.server.URL,
		"YONA_SSH_SECRET_FILE":  writeFile(t, dir, "secret", "s3cret"),
		"YONA_SSH_RELAY_SOCKET": "/var/lib/yona/ssh/relay.sock",
		"YONA_SSH_CONFIG":       filepath.Join(dir, "none.conf"),
	}
	return f
}

func callAuth(f *authFixture, args ...string) (int, string, string) {
	var stdout, stderr bytes.Buffer
	code := run(append([]string{"auth"}, args...), env(f.env), strings.NewReader(""), &stdout, &stderr, "/usr/local/bin/yona-ssh")
	return code, stdout.String(), stderr.String()
}

func TestAuthPrintsAuthorizedKeysLine(t *testing.T) {
	f := newAuthFixture(t, 200, `{"principal":"v1.user.7.4102444800.abcDEF-_0"}`)
	code, out, _ := callAuth(f, "ssh-ed25519", "AAAAC3Nza")
	if code != 0 {
		t.Fatalf("exit %d", code)
	}
	want := `command="/usr/local/bin/yona-ssh relay --socket /var/lib/yona/ssh/relay.sock v1.user.7.4102444800.abcDEF-_0",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty ssh-ed25519 AAAAC3Nza` + "\n"
	if out != want {
		t.Fatalf("출력이 다르다:\n%q\n%q", out, want)
	}
	if f.received.Header.Get("X-Yona-Internal-Secret") != "s3cret" || f.received.URL.Path != "/internal/ssh/authenticate" || f.received.Method != "POST" {
		t.Fatalf("요청이 다르다: %v %v", f.received.Method, f.received.URL.Path)
	}
	if f.body["publicKey"] != "ssh-ed25519 AAAAC3Nza" {
		t.Fatalf("본문이 다르다: %v", f.body)
	}
}

func TestAuthPrintsNothingWhenUnknownOrFailing(t *testing.T) {
	cases := map[string]struct {
		status int
		reply  string
	}{
		"모르는 키(빈 응답)": {200, `{}`},
		"거부":          {403, `{"message":"no"}`},
		"서버 오류":       {500, `oops`},
		"이상한 표":       {200, `{"principal":"a b;rm -rf /"}`},
		"따옴표가 든 표":    {200, `{"principal":"abc\"def"}`},
		"깨진 JSON":     {200, `not json`},
	}
	for name, c := range cases {
		t.Run(name, func(t *testing.T) {
			f := newAuthFixture(t, c.status, c.reply)
			code, out, _ := callAuth(f, "ssh-ed25519", "AAAA")
			if code != 0 || out != "" {
				t.Fatalf("아무것도 인쇄하지 않고 0이어야 한다: %d %q", code, out)
			}
		})
	}
}

func TestAuthFailsClosedOnBadInput(t *testing.T) {
	f := newAuthFixture(t, 200, `{"principal":"v1.user.1.2.3"}`)
	if code, out, _ := callAuth(f); code != 0 || out != "" {
		t.Fatalf("인자가 없으면 조용히 끝난다: %d %q", code, out)
	}
	if code, out, _ := callAuth(f, "ssh-ed25519"); code != 0 || out != "" {
		t.Fatalf("본문이 없으면 조용히 끝난다: %d %q", code, out)
	}
	if _, out, _ := callAuth(f, "ssh-ed25519", "AA AA"); out != "" {
		t.Fatalf("공백이 든 본문은 거부한다: %q", out)
	}
	if _, out, _ := callAuth(f, "ssh-ed25519\n", "AAAA"); out != "" {
		t.Fatalf("개행이 든 종류는 거부한다: %q", out)
	}
	f.env["YONA_SSH_SECRET_FILE"] = filepath.Join(t.TempDir(), "missing")
	if code, out, _ := callAuth(f, "ssh-ed25519", "AAAA"); code != 0 || out != "" {
		t.Fatalf("비밀 값 파일이 없으면 조용히 끝난다: %d %q", code, out)
	}
}

func TestAuthRefusesUnsafePathsInForcedCommand(t *testing.T) {
	f := newAuthFixture(t, 200, `{"principal":"v1.user.1.2.3"}`)
	f.env["YONA_SSH_RELAY_SOCKET"] = "/var/lib/yona/ssh dir/relay.sock"
	if _, out, _ := callAuth(f, "ssh-ed25519", "AAAA"); out != "" {
		t.Fatalf("셸이 해석할 수 있는 문자가 든 소켓 경로는 거부한다: %q", out)
	}
	f.env["YONA_SSH_RELAY_SOCKET"] = `/tmp/a"b`
	if _, out, _ := callAuth(f, "ssh-ed25519", "AAAA"); out != "" {
		t.Fatalf("따옴표가 든 경로는 거부한다: %q", out)
	}
}

func TestConfigFileOverridesEnvironmentAndAllowsQuotes(t *testing.T) {
	f := newAuthFixture(t, 200, `{"principal":"v1.user.9.2.3"}`)
	dir := t.TempDir()
	conf := writeFile(t, dir, "ssh-auth.conf", "# 주석\n\nexport YONA_INTERNAL_URL=\""+f.server.URL+"\"\nYONA_SSH_RELAY_SOCKET='/run/yona/relay.sock'\n")
	f.env["YONA_INTERNAL_URL"] = "http://127.0.0.1:1"
	f.env["YONA_SSH_CONFIG"] = conf
	_, out, _ := callAuth(f, "ssh-ed25519", "AAAA")
	if !strings.Contains(out, "--socket /run/yona/relay.sock v1.user.9.2.3") {
		t.Fatalf("설정 파일 값이 환경 변수를 이겨야 한다: %q", out)
	}
}

func TestAuthTimesOutQuietly(t *testing.T) {
	slow := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { time.Sleep(300 * time.Millisecond) }))
	defer slow.Close()
	f := newAuthFixture(t, 200, `{}`)
	f.env["YONA_INTERNAL_URL"] = slow.URL
	old := authTimeout
	authTimeout = 50 * time.Millisecond
	defer func() { authTimeout = old }()
	if code, out, _ := callAuth(f, "ssh-ed25519", "AAAA"); code != 0 || out != "" {
		t.Fatalf("시간이 지나면 조용히 끝난다: %d %q", code, out)
	}
}

// relayFixture는 가짜 중계 소켓 서버이다.
func newSocket(t *testing.T) (string, net.Listener) {
	t.Helper()
	dir, err := os.MkdirTemp("", "y")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { os.RemoveAll(dir) })
	path := filepath.Join(dir, "r.sock")
	listener, err := net.Listen("unix", path)
	if err != nil {
		t.Skip("유닉스 소켓을 쓸 수 없는 환경이다: ", err)
	}
	t.Cleanup(func() { listener.Close() })
	return path, listener
}

func callRelay(socket string, environment map[string]string, stdin io.Reader) (int, string, string) {
	var stdout, stderr bytes.Buffer
	code := run([]string{"relay", "--socket", socket, "v1.user.1.2.3"}, env(environment), stdin, &stdout, &stderr, "/x/yona-ssh")
	return code, stdout.String(), stderr.String()
}

func TestRelayWithoutCommandGivesNoShell(t *testing.T) {
	code, _, stderr := callRelay("/nonexistent.sock", map[string]string{}, strings.NewReader(""))
	if code != 1 || !strings.Contains(stderr, "Interactive shell access is not provided") {
		t.Fatalf("원본 명령이 없으면 셸을 주지 않는다: %d %q", code, stderr)
	}
}

func TestRelayRejectsNewlinesInCommand(t *testing.T) {
	path, _ := newSocket(t)
	for _, bad := range []string{"git-upload-pack 'a'\nINJECT", "git-upload-pack 'a'\rINJECT"} {
		code, _, stderr := callRelay(path, map[string]string{"SSH_ORIGINAL_COMMAND": bad, "SSH_CLIENT": "1.2.3.4 5 22"}, strings.NewReader(""))
		if code != 1 || stderr == "" {
			t.Fatalf("개행이 든 명령은 거부한다: %d %q", code, stderr)
		}
	}
}

func TestRelayHandshakeAndBidirectionalCopy(t *testing.T) {
	path, listener := newSocket(t)
	got := make(chan string, 1)
	go func() {
		conn, err := listener.Accept()
		if err != nil {
			return
		}
		defer conn.Close()
		// 세 줄을 읽고, 그 뒤 입력을 대문자로 돌려준다. 클라이언트 쪽 쓰기가 끝나면(EOF) 응답을 마치고 닫는다.
		data, _ := io.ReadAll(conn)
		got <- string(data)
		_, _ = conn.Write([]byte(strings.ToUpper("reply:" + string(data[strings.LastIndex(string(data), "\n")+1:]))))
	}()
	code, stdout, _ := callRelay(path, map[string]string{"SSH_ORIGINAL_COMMAND": "git-receive-pack '/o/p.git'", "SSH_CLIENT": "203.0.113.9 50000 22"}, strings.NewReader("payload"))
	if code != 0 {
		t.Fatalf("exit %d", code)
	}
	if want := "v1.user.1.2.3\n203.0.113.9\ngit-receive-pack '/o/p.git'\npayload"; <-got != want {
		t.Fatalf("핸드셰이크가 다르다")
	}
	if stdout != "REPLY:PAYLOAD" {
		t.Fatalf("응답이 다르다: %q", stdout)
	}
}

func TestRelayEndsWhenServerClosesEvenIfInputStaysOpen(t *testing.T) {
	path, listener := newSocket(t)
	go func() {
		conn, err := listener.Accept()
		if err != nil {
			return
		}
		buf := make([]byte, 4096)
		_, _ = conn.Read(buf)
		_, _ = conn.Write([]byte("0013ERR no access\n"))
		conn.Close() // 서버가 먼저 끝낸다
	}()
	reader, writer := io.Pipe() // 닫히지 않는 입력(클라이언트가 아직 보내는 중인 상태)
	defer writer.Close()
	done := make(chan struct{})
	var code int
	var stdout string
	go func() {
		code, stdout, _ = callRelay(path, map[string]string{"SSH_ORIGINAL_COMMAND": "git-upload-pack 'x'"}, reader)
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(5 * time.Second):
		t.Fatal("서버가 닫았는데도 중계가 끝나지 않는다")
	}
	if code != 0 || !strings.Contains(stdout, "ERR no access") {
		t.Fatalf("%d %q", code, stdout)
	}
}

func TestRelayReportsConnectFailure(t *testing.T) {
	code, _, stderr := callRelay(filepath.Join(t.TempDir(), "none.sock"), map[string]string{"SSH_ORIGINAL_COMMAND": "git-upload-pack 'x'"}, strings.NewReader(""))
	if code != 1 || !strings.Contains(stderr, "yona-ssh") {
		t.Fatalf("연결하지 못하면 1과 설명을 낸다: %d %q", code, stderr)
	}
}

func TestUnknownSubcommandFails(t *testing.T) {
	var stdout, stderr bytes.Buffer
	if code := run([]string{"nope"}, env(nil), strings.NewReader(""), &stdout, &stderr, "/x"); code != 2 {
		t.Fatalf("알 수 없는 명령은 2이다: %d", code)
	}
}
