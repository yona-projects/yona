package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 기본 설정(`yona.send-yona-usage=true`)에서 UA 태그 대신 GA4 태그(X-21)가 나오고, 측정 ID가 들어가고(설치 ID는 보내지 않는다) 경로를 가려서 보내는지 검증한다.
 */
@AutoConfigureMockMvc
class AnalyticsTagTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun login() = mockMvc.perform(get("/users/loginform")).andReturn().response.contentAsString

    @Test
    fun `기본 설정에서 UA 태그 대신 GA4 태그가 나온다`() {
        val html = login()
        assertTrue(html.contains("googletagmanager.com/gtag/js"))
        assertTrue(html.contains("G-CKTN17HLPP"))
        assertTrue(html.contains("maskPath"))
        assertFalse(html.contains("install_id"))
        assertFalse(html.contains("google-analytics.com"))
        assertFalse(html.contains("UA-102735758-1"))
    }

    @Test
    fun `2단 보기는 켜진 상태와 토글 클릭을 화면 종류와 함께 보낸다`() {
        val html = login()
        // 화면 분류는 v1.6 경로를 따른다: 프로젝트 이슈, 글, 풀 리퀘스트 목록, 내 이슈, 조직 이슈와 글, 사용자 화면
        listOf("project_issues", "project_posts", "project_pulls", "my_issues", "org_issues", "org_boards", "user_profile").forEach {
            assertTrue(html.contains("'$it'"), "$it 분류가 있어야 한다")
        }
        assertTrue(html.contains("pullRequests|closedPullRequests|sentPullRequests"))
        assertTrue(html.contains("organizations"))
        // 토글 이벤트에도 어느 화면에서 눌렀는지를 싣는다
        assertTrue(Regex("two_column_on.*?screen: twoColumnScreen\\(location.pathname\\)", RegexOption.DOT_MATCHES_ALL).containsMatchIn(html))
    }

    @Test
    fun `경로 가리기는 v1_6의 고정 경로를 예약어로 안다`() {
        val html = login()
        listOf("'sites'", "'-_-api'", "'organizations'", "'authenticate'", "'markdown'", "'notification'", "'_help'", "'assets'").forEach {
            assertTrue(html.contains(it), "$it 예약어가 있어야 한다")
        }
    }

    @Test
    fun `2단 보기가 켜진 채 목록 제목을 열면 two_column_open을 화면 종류와 프레임 여부와 함께 보낸다`() {
        val html = login()
        assertTrue(html.contains("two_column_open"))
        assertTrue(html.contains(".title, .twoColumeModeTarget"))
        assertTrue(Regex("two_column_open.*?screen: twoColumnScreen\\(location.pathname\\).*?in_frame", RegexOption.DOT_MATCHES_ALL).containsMatchIn(html))
    }
}
