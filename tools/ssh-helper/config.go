package main

import (
	"bufio"
	"os"
	"strings"
)

// 설정 파일(`/etc/yona/ssh-auth.conf`)의 이름과 기본값이다. 기존 셸 훅(`tools/ssh/yona-ssh-auth.sh`)과 같은 키를 쓴다.
const (
	defaultConfigFile = "/etc/yona/ssh-auth.conf"
	defaultURL        = "http://127.0.0.1:9000"
	defaultSecretFile = "/etc/yona/ssh-internal-secret"
	defaultSocket     = "/var/lib/yona/ssh/relay.sock"
)

// config는 훅이 쓰는 설정이다.
type config struct {
	URL        string // Yona 서버의 내부 주소(같은 호스트여야 한다)
	SecretFile string // 공유 비밀 값을 담은 파일
	Socket     string // 중계 소켓 경로
}

// loadConfig는 설정을 읽는다. sshd는 훅을 최소한의 환경으로 실행해 환경 변수를 줄 수 없으므로 운영에서는 파일을 쓰고,
// 환경 변수는 시험과 수동 실행을 위한 기본값이다. 셸 훅이 파일을 `source`해 환경 변수를 덮어쓰던 것과 같게 파일이 이긴다.
func loadConfig(getenv func(string) string) config {
	values := map[string]string{
		"YONA_INTERNAL_URL":     getenv("YONA_INTERNAL_URL"),
		"YONA_SSH_SECRET_FILE":  getenv("YONA_SSH_SECRET_FILE"),
		"YONA_SSH_RELAY_SOCKET": getenv("YONA_SSH_RELAY_SOCKET"),
	}
	path := getenv("YONA_SSH_CONFIG")
	if path == "" {
		path = defaultConfigFile
	}
	for name, value := range readConfigFile(path) {
		values[name] = value
	}
	return config{
		URL:        orDefault(values["YONA_INTERNAL_URL"], defaultURL),
		SecretFile: orDefault(values["YONA_SSH_SECRET_FILE"], defaultSecretFile),
		Socket:     orDefault(values["YONA_SSH_RELAY_SOCKET"], defaultSocket),
	}
}

func orDefault(value, fallback string) string {
	if value == "" {
		return fallback
	}
	return value
}

// readConfigFile은 `이름=값` 줄을 읽는다. 빈 줄과 `#` 주석을 건너뛰고, 줄머리의 `export `와 값을 감싼 따옴표를 허용한다. 읽을 수 없으면 빈 맵이다.
func readConfigFile(path string) map[string]string {
	result := map[string]string{}
	file, err := os.Open(path)
	if err != nil {
		return result
	}
	defer file.Close()
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		line = strings.TrimSpace(strings.TrimPrefix(line, "export "))
		name, value, found := strings.Cut(line, "=")
		if !found {
			continue
		}
		value = strings.TrimSpace(value)
		if len(value) >= 2 && (value[0] == '"' || value[0] == '\'') && value[len(value)-1] == value[0] {
			value = value[1 : len(value)-1]
		}
		result[strings.TrimSpace(name)] = value
	}
	return result
}
