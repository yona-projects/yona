package dev.yona.domain.project

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 프로젝트 이름 규칙을 검증한다. v1.6 `Project.name`의 `@Pattern`, `@Restricted`와, 위키 저장소(`<이름>.wiki.git`)와의
 * 충돌을 막으려고 더한 `.wiki` 끝맺음 금지(X-17, D-60)이다.
 */
class ProjectNameRulesTest {

    @Test
    fun `v1 6의 예약어는 대소문자를 가리지 않고 거부한다`() {
        listOf(".", "..", ".git", ".GIT").forEach { assertTrue(ProjectNameRules.RESTRICTED in ProjectNameRules.violations(it), it) }
    }

    @Test
    fun `점 wiki 로 끝나는 이름은 예약어로 거부한다`() {
        listOf("demo.wiki", "Demo.WIKI", "a.b.wiki", "한글.wiki").forEach {
            assertEquals(listOf(ProjectNameRules.RESTRICTED), ProjectNameRules.violations(it), it)
        }
    }

    @Test
    fun `점 wiki 가 끝이 아니면 허용한다`() {
        listOf("wiki", "my-wiki", "demo.wiki2", "demo.wikis", "wiki.demo", ".wikix").forEach {
            assertTrue(ProjectNameRules.violations(it).isEmpty(), it)
        }
    }
}
