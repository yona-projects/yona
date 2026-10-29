package dev.yona.web.pullrequest

import dev.yona.web.organization.OrganizationListAssembler
import dev.yona.web.project.ProjectHomeTabsAssembler
import dev.yona.web.user.UserPageController
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.transaction.annotation.Transactional
import kotlin.reflect.KClass
import kotlin.test.assertTrue

/**
 * 풀 리퀘스트 줄이 담당자와 라벨(지연 로딩 컬렉션)을 읽는 화면의 진입점이 트랜잭션 안에서 도는지 검증한다(X-14, D-34 15번).
 * 통합 시험은 전체가 트랜잭션이라 세션 밖 지연 로딩 오류(`LazyInitializationException`)를 드러내지 못하므로, 실제 서버에서 생긴 이 오류(대시보드 500)를
 * 막는 장치를 구조로 확인한다.
 */
class PullRequestListsTransactionTest {

    private fun assertTransactional(type: KClass<*>, method: String) {
        val found = type.java.methods.filter { it.name == method }
        assertTrue(found.isNotEmpty(), "${type.simpleName}.$method 가 없다")
        found.forEach {
            assertTrue(
                AnnotatedElementUtils.hasAnnotation(it, Transactional::class.java) || AnnotatedElementUtils.hasAnnotation(type.java, Transactional::class.java),
                "${type.simpleName}.$method 는 지연 로딩 컬렉션을 읽으므로 @Transactional 이어야 한다"
            )
        }
    }

    @Test
    fun `프로젝트 홈 대시보드 조립기는 트랜잭션 안에서 돈다`() = assertTransactional(ProjectHomeTabsAssembler::class, "dashboard")

    @Test
    fun `조직 풀 리퀘스트 줄 조립기는 트랜잭션 안에서 돈다`() = assertTransactional(OrganizationListAssembler::class, "pullRequestRows")

    @Test
    fun `사용자 화면 컨트롤러는 트랜잭션 안에서 돈다`() = assertTransactional(UserPageController::class, "userInfo")
}
