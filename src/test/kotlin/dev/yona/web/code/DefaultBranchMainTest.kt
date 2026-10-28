package dev.yona.web.code

import dev.yona.domain.project.Project
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals

/**
 * 새 저장소의 기본 브랜치가 `main`이면(X-22, 기본 설정) 저장소 서비스가 그 이름을 돌려주는지 스프링 컨텍스트에서 검증한다.
 * 공용 통합 시험 설정은 v1.6 가정 때문에 `master`로 두므로 여기서만 기본값(`main`)으로 되돌린다.
 */
@TestPropertySource(properties = ["yona.git-default-branch=main", "yona.data-dir=build/tmp/it-default-branch-main"])
class DefaultBranchMainTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repositories: RepositoryService

    @Test
    fun `새로 만든 저장소의 기본 브랜치는 main이다`() {
        val project = Project(owner = "mainowner", name = "mainproj", vcs = "GIT")
        repositories.createRepository(project)
        assertEquals("refs/heads/main", repositories.defaultBranch(project))
    }
}
