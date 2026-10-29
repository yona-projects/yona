package dev.yona.audit

import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.io.File
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 메시지 번들이 v1.6 `conf/messages*`와 같은 키를 가지는지, 템플릿이 참조하는 키가 모두 번들에 있는지 대조한다(동등성 점검 9b).
 *
 * `audit/v16-messages-*.txt`는 v1.6 번들 파일에서 뽑은 키 스냅샷이다. 값은 설정 이름 하나(`error.tooLargeText.admin`)를 빼고 같다(점검 시점에 확인).
 */
class MessageParityTest {

    private fun bundleKeys(file: String): Set<String> = Properties().also { props ->
        File("src/main/resources/$file").reader(Charsets.UTF_8).use { props.load(it) }
    }.stringPropertyNames()

    private fun snapshot(tag: String): Set<String> =
        javaClass.getResourceAsStream("/audit/v16-messages-$tag.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.toSet()

    /**
     * v1.6에 없고 추가 개발 항목(D-28)에서 일부러 더한 키이다. 키를 더할 때는 `decisions.md`에 근거를 적고 여기에도 올린다.
     * 모든 언어 번들에 이 키들이 있어야 한다(없으면 영어로 대체되는 키가 생긴다).
     * - X-18 Git 태그 화면(D-30): 태그 탭과 태그 화면의 글
     * - 브랜치 삭제 확인창(D-31): `code.branches.deleteConfirm`
     */
    private val addedKeys = setOf(
        "title.tags", "code.tags.annotated", "code.tags.lightweight", "code.tags.notCommit", "code.tags.empty", "code.tags.deleteConfirm",
        "code.branches.deleteConfirm"
    )

    @Test
    fun `언어별 번들의 키는 v1_6과 같고 추가한 키만 더 있다`() {
        mapOf(
            "default" to "messages.properties", "ja_JP" to "messages_ja_JP.properties", "ko_KR" to "messages_ko_KR.properties",
            "ru_RU" to "messages_ru_RU.properties", "uz_UZ" to "messages_uz_UZ.properties"
        ).forEach { (tag, file) ->
            val v16 = snapshot(tag); val v2 = bundleKeys(file)
            assertEquals(emptySet(), v16 - v2, "$file: v1.6에 있고 v2에 없는 키")
            assertEquals(addedKeys, v2 - v16, "$file: v2에만 있는 키는 의도적으로 더한 키와 같아야 한다")
        }
    }

    @Test
    fun `템플릿이 참조하는 메시지 키는 모두 번들에 있다`() {
        val keys = bundleKeys("messages.properties")
        val comment = Regex("<!--/\\*.*?\\*/-->", RegexOption.DOT_MATCHES_ALL)
        val refs = Regex("#\\{([A-Za-z0-9_.\\-]+)[\\s,)}]|#messages\\.msg(?:OrNull)?\\('([A-Za-z0-9_.\\-]+)'")
        val missing = PathMatchingResourcePatternResolver().getResources("file:src/main/resources/templates/**/*.html").flatMap { resource ->
            val text = comment.replace(resource.file.readText(Charsets.UTF_8), "")
            refs.findAll(text).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.filter { it !in keys }.map { "${resource.file.name}: $it" }.toList()
        }
        assertTrue(missing.isEmpty(), "번들에 없는 키: $missing")
    }
}
