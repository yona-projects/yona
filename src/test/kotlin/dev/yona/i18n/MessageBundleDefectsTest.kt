package dev.yona.i18n

import org.junit.jupiter.api.Test
import java.io.InputStreamReader
import java.text.MessageFormat
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 메시지 번들의 알려진 결함 수정을 검증한다(`architecture/v16-safe-fix-candidates.md`).
 *
 * v1.6 번들에는 코드가 쓰는 키가 빠져 화면에 키 이름이 그대로 나오거나, 메시지 인자 번호가 어긋나거나, 올바르지 않은 자리표시자가 있었다.
 */
class MessageBundleDefectsTest {

    private val bundles = listOf("messages.properties", "messages_ko_KR.properties", "messages_ja_JP.properties", "messages_ru_RU.properties", "messages_uz_UZ.properties")

    private fun load(name: String): Properties = Properties().also { props ->
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "번들이 없다: $name" }.use { props.load(InputStreamReader(it, Charsets.UTF_8)) }
    }

    @Test
    fun `코드가 쓰는 키가 모든 번들에 있다`() {
        // 키 이름이 그대로 보이던 것들이다: 게시글 댓글 검색의 작성자 없음(C028), 로그인 화면 제목의 오타 키(C152), 통계 화면 제목(R-51-3), 리뷰 엑셀의 댓글 작성 시각 머리글(R-67)
        val required = listOf("posting.noAuthor", "error.fobidden", "statistics", "common.comment.created")
        for (bundle in bundles) {
            val props = load(bundle)
            for (key in required) assertTrue(props.getProperty(key).orEmpty().isNotBlank(), "$bundle 에 $key 가 없다")
        }
    }

    @Test
    fun `작성자 없음과 권한 없음 키는 같은 뜻의 기존 키와 같은 문구이다`() {
        for (bundle in bundles) {
            val props = load(bundle)
            assertEquals(props.getProperty("common.noAuthor"), props.getProperty("posting.noAuthor"), "$bundle posting.noAuthor")
            assertEquals(props.getProperty("error.forbidden"), props.getProperty("error.fobidden"), "$bundle error.fobidden")
        }
    }

    @Test
    fun `메일 생성 권한 오류 문구는 인자 0부터 2까지만 쓴다`() {
        // 인자는 (사용자, 종류, 프로젝트) 세 개이다. {1}부터 {3}까지 쓰면 문구가 어긋나고 {3}이 치환되지 않는다(C184)
        for (bundle in bundles) {
            val pattern = load(bundle).getProperty("viaEmail.error.cannotCreate") ?: continue
            val indexes = Regex("\\{(\\d+)}").findAll(pattern).map { it.groupValues[1].toInt() }.toSet()
            assertTrue(indexes.isNotEmpty() && indexes.all { it in 0..2 }, "$bundle 의 인자 번호 $indexes")
            assertEquals(3, indexes.size, "$bundle 은 세 인자를 모두 써야 한다")
        }
    }

    @Test
    fun `프로젝트를 지켜보는 중 문구는 올바른 MessageFormat 패턴이다`() {
        // 러시아어는 {-}라는 올바르지 않은 자리표시자를 써서 이 문구를 그리는 화면이 500이었다(R-72)
        for (bundle in bundles) {
            val pattern = load(bundle).getProperty("project.you.are.watching") ?: continue
            val rendered = MessageFormat(pattern).format(arrayOf("owner/project"))
            assertTrue(rendered.contains("owner/project"), "$bundle: $rendered")
        }
    }
}
