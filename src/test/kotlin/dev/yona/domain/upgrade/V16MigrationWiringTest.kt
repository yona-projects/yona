package dev.yona.domain.upgrade

import dev.yona.domain.apitoken.ApiTokenStartup
import dev.yona.domain.project.ReviewerSettingMigration
import dev.yona.domain.pullrequest.PullRequestReviewBackfill
import dev.yona.domain.pullrequest.PullRequestStartup
import org.junit.jupiter.api.Test
import org.springframework.boot.ApplicationRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 서버를 시작할 때 자동으로 도는 것은 이관 안내와 오래 쓰지 않은 토큰 정리뿐이고, 데이터 보정 4건은 이관 명령으로만 돈다. */
class V16MigrationWiringTest {

    @Test
    fun `데이터 보정 네 가지는 서버를 시작할 때 자동으로 돌지 않는다`() {
        listOf(PullRequestStartup::class.java, PullRequestReviewBackfill::class.java, ReviewerSettingMigration::class.java).forEach {
            assertFalse(ApplicationRunner::class.java.isAssignableFrom(it), "${it.simpleName}는 시작할 때 도는 ApplicationRunner가 아니어야 한다")
        }
    }

    @Test
    fun `시작할 때 도는 것은 오래 쓰지 않은 토큰 정리와 미적용 안내와 이관 명령 실행이다`() {
        assertTrue(ApplicationRunner::class.java.isAssignableFrom(ApiTokenStartup::class.java))
        assertTrue(ApplicationRunner::class.java.isAssignableFrom(V16MigrationNotice::class.java))
        assertTrue(ApplicationRunner::class.java.isAssignableFrom(V16MigrationCommand::class.java))
    }
}
