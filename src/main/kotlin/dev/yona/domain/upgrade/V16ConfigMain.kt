package dev.yona.domain.upgrade

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.system.exitProcess

/**
 * v1.6의 `application.conf`를 v2 속성 파일로 바꾸는 명령줄 진입점이다([V16ConfigConverter]). 스프링 컨텍스트와 데이터베이스를 열지 않는다.
 * 이관 스크립트(`scripts/migrate-v16.sh --v16-conf=...`)가 부트 jar의 `PropertiesLauncher`로 부른다.
 *
 * 사용법: `V16ConfigMain <application.conf> <출력 속성 파일> [--force]`
 *
 * 종료 코드: 0 성공, 1 실패(입력 없음, 출력이 이미 있는데 `--force`가 없음), 2 사용법 오류.
 * 출력 파일에는 비밀번호와 비밀 키가 들어갈 수 있으므로 만들 때 소유자만 읽고 쓰도록 권한을 좁힌다(지원하는 파일 시스템에서).
 */
object V16ConfigMain {

    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(run(args.toList()))
    }

    /** 명령을 실행하고 종료 코드를 돌려준다. 시험에서 부르기 위해 [main]과 나눴다. */
    fun run(args: List<String>, out: (String) -> Unit = ::println): Int {
        val force = "--force" in args
        val paths = args.filter { !it.startsWith("--") }
        if (paths.size != 2) {
            out("사용법: V16ConfigMain <application.conf> <출력 속성 파일> [--force]")
            return 2
        }
        val input = Path.of(paths[0])
        val output = Path.of(paths[1])
        if (!Files.isRegularFile(input)) {
            out("[v1.6 설정] 입력 파일이 없습니다: $input")
            return 1
        }
        if (Files.exists(output) && !force) {
            out("[v1.6 설정] 출력 파일이 이미 있습니다. 덮어쓰려면 --force를 주세요: $output")
            return 1
        }
        val parsed = V16ConfigConverter.parse(Files.readString(input))
        val converted = V16ConfigConverter.convert(parsed)
        Files.writeString(output, V16ConfigConverter.render(converted))
        runCatching { Files.setPosixFilePermissions(output, PosixFilePermissions.fromString("rw-------")) }

        out("[v1.6 설정] ${converted.properties.size}개 키를 변환해 $output 에 썼습니다")
        converted.notes.forEach { out("[v1.6 설정] 참고: $it") }
        if (converted.removed.isNotEmpty()) out("[v1.6 설정] v2에서 없어진 기능이라 옮기지 않은 키: ${converted.removed.joinToString()}")
        if (converted.unmapped.isNotEmpty()) out("[v1.6 설정] 대응하는 v2 키를 모르는 키(직접 확인하세요): ${converted.unmapped.joinToString()}")
        if (converted.sensitive.isNotEmpty()) out("[v1.6 설정] 비밀이 들어 있는 키: ${converted.sensitive.joinToString()} (파일 권한을 소유자 전용으로 좁혔습니다)")
        return 0
    }
}
