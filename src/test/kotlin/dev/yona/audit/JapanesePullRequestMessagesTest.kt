package dev.yona.audit

import org.junit.jupiter.api.Test
import java.util.Properties
import kotlin.test.assertTrue

/**
 * 풀 리퀘스트 화면이 쓰는 `pullRequest.*` 메시지 중 일본어 번들에 빠져 있던 7개를 채웠는지 검증한다(UI 계약 재조사, code-F15).
 * 일본어 번들에 없으면 기본 번들(영어)로 대체되어 일본어 화면에 영어가 섞여 보인다. 값에는 일본어 글자(히라가나, 가타카나, 한자)가 있어야 한다.
 */
class JapanesePullRequestMessagesTest {

    private val ja: Properties = Properties().also { props ->
        JapanesePullRequestMessagesTest::class.java.getResourceAsStream("/messages_ja_JP.properties")!!.reader(Charsets.UTF_8).use { props.load(it) }
    }

    @Test
    fun `풀 리퀘스트 화면의 일곱 메시지가 일본어로 있다`() {
        val keys = listOf(
            "pullRequest.additional.changes", "pullRequest.changes.all", "pullRequest.error.newPullRequestForm",
            "pullRequest.merge", "pullRequest.review", "pullRequest.review.closed", "pullRequest.review.total"
        )
        for (key in keys) {
            val value = ja.getProperty(key)
            assertTrue(value != null && value.any { it in '぀'..'ヿ' || it in '一'..'鿿' }, "$key 가 일본어 번들에 없거나 일본어가 아니다: $value")
        }
        // 오류 메시지는 두 자리 표시자와 줄바꿈 표시를 그대로 가진다
        assertTrue(ja.getProperty("pullRequest.error.newPullRequestForm")!!.let { it.contains("<br>") && it.contains("{0}") && it.contains("{1}") })
    }
}
