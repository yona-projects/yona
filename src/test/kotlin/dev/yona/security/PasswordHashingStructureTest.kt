package dev.yona.security

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * 비밀번호 해시를 다루는 곳이 [UserPasswordService] 한 곳으로 모였는지 지키는 구조 시험이다(로드맵 X-43 1단계 작업 1-3, `decisions.md` D-129).
 * 운영 코드(`src/main`)가 [LegacyPasswordHasher]를 직접 부르면 실패한다. 해시를 만들고 확인하는 곳이 흩어지면 해시 강화(argon2id)를 할 때 빠뜨리기 쉽기 때문이다.
 * 시험 코드는 사용자를 만들기 위해 해시를 직접 쓰는 것이 자연스러워 검사하지 않는다.
 */
class PasswordHashingStructureTest {

    /** 해시 알고리즘을 직접 다뤄도 되는 파일이다. */
    private val allowed = setOf("UserPasswordService.kt", "LegacyPasswordEncoder.kt", "LegacyPasswordHasher.kt")

    @Test
    fun `운영 코드는 LegacyPasswordHasher를 UserPasswordService 밖에서 부르지 않는다`() {
        val offenders = File("src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in allowed }
            .filter { Regex("""LegacyPasswordHasher\s*\.""").containsMatchIn(it.readText()) }
            .map { it.path.removePrefix("src/main/kotlin/dev/yona/") }.sorted().toList()
        assertEquals(emptyList(), offenders, "UserPasswordService를 거치지 않고 해시를 직접 다루는 파일")
    }

    @Test
    fun `해시 비교는 상수 시간 비교만 쓴다`() {
        val offenders = File("src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { Regex("""\.password\s*(==|!=)(?!\s*null\b)""").containsMatchIn(it.readText()) }
            .map { it.path.removePrefix("src/main/kotlin/dev/yona/") }.sorted().toList()
        assertEquals(emptyList(), offenders, "`user.password`를 `==`로 비교하는 파일(널 검사는 제외)")
    }
}
