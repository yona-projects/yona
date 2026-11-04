package dev.yona.config

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 마이그레이션용 GitHub 앱 설정의 기본 안내 문구에 오타가 없는지 확인한다(R-58-5). v1.6 기본값은 `CILENT`라고 적혀 있었다. */
class YonaPropertiesGithubDefaultsTest {

    @Test
    fun `GitHub 클라이언트 기본 안내 문구는 CLIENT로 적는다`() {
        val github = YonaProperties().github
        assertFalse(github.clientId.contains("CILENT") || github.clientSecret.contains("CILENT"), "오타 CILENT")
        assertTrue(github.clientId.contains("CLIENT ID") && github.clientSecret.contains("CLIENT SECRET"))
    }
}
