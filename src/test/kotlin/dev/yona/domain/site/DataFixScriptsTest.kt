package dev.yona.domain.site

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `tools/data-fix`의 교정 스크립트가 현재 스키마에서 실행되는지 검증한다(표나 컬럼 이름이 틀리면 운영자가 실행할 때에야 알게 된다).
 * 확인 구문(1단계)과 주석을 푼 UPDATE(2단계)를 모두 실행한다. 트랜잭션은 되돌린다. NULL 복원 스크립트의 효과는 외래 키가 막아서 시험 데이터로 만들 수 없다
 * (가져오기가 외래 키 검사를 잠시 끄기 때문에 운영 데이터에만 0이 남는다).
 */
@Transactional
class DataFixScriptsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var jdbc: JdbcTemplate

    private fun script(name: String) = File("tools/data-fix/$name").readLines()

    /** 주석을 풀어 실행할 수 있는 구문 목록이다. 구문은 `;`로 끝난다. `-- update`로 시작하는 줄은 풀고 다른 주석은 버린다. */
    private fun statements(lines: List<String>): List<String> {
        val body = lines.map { if (it.startsWith("-- update")) it.removePrefix("-- ") else it }.filter { !it.startsWith("--") && it.isNotBlank() }
        return body.joinToString("\n").split(";").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @Test
    fun `NULL 복원 스크립트의 확인 구문과 UPDATE가 현재 스키마에서 실행된다 (R-56-6)`() {
        val all = statements(script("restore-null-ids.sql"))
        assertTrue(all.size > 40, "SELECT 하나와 컬럼마다의 UPDATE가 있어야 한다: ${all.size}")
        assertTrue(all.first().startsWith("select"))
        all.forEach { jdbc.execute(it) }
    }

    @Test
    fun `prev_commit_id 통일 스크립트의 확인 구문과 두 UPDATE가 실행된다 (R-44-3)`() {
        jdbc.update("insert into comment_thread(dtype, prev_commit_id, commit_id, path) values ('ranged', '', 'c1', 'a.txt')")
        jdbc.update("insert into comment_thread(dtype, prev_commit_id, commit_id, path) values ('non_ranged', null, 'c2', 'b.txt')")
        val all = statements(script("normalize-prev-commit-id.sql"))
        assertEquals(3, all.size)
        jdbc.queryForList(all[0])
        jdbc.execute(all[1])
        assertEquals(0, jdbc.queryForObject("select count(*) from comment_thread where prev_commit_id = ''", Int::class.java))
        jdbc.execute(all[2])
        assertEquals(0, jdbc.queryForObject("select count(*) from comment_thread where prev_commit_id is null", Int::class.java))
    }
}
