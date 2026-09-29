package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.io.File

// TASK-0421(P3-02 11라운드, 버그9) — 실서버(H2 프로파일) + 실제 yona-cli 바이너리로 `project delete`
// → 같은 owner/name으로 재생성/재-fork를 반복 검증하던 중 발견. ProjectServiceImpl.deleteProject()가
// DB의 Project 행은 지우지만 {yona.git.base-dir}/{owner}/{project}.git 물리 bare 저장소 디렉터리는
// 파일시스템에 그대로 남겨, 이후 같은 owner/name으로 새 프로젝트를 만들면(createProject의
// repositoryService.getRepository(project).create() -> GitRepository.create()가 이미 존재하는
// 디렉터리와 충돌) FileAlreadyExistsException이 그대로 500으로 튄다(실측 확인). changeVCS()가 이미
// 쓰고 있는 "getRepository(project).delete() 후 재생성" 패턴을 deleteProject()에도 적용해 고쳤다 —
// 이 스펙은 mockk가 아닌 실제 RepositoryService/GitRepository + 실제 파일시스템으로 그 수정을
// 고정한다.
class ProjectDeletePhysicalRepositoryIntegrationSpec @Autowired constructor(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectService: ProjectService
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private val ownerName = "del-phys-owner"
    private val projName = "del-phys-repo"
    // ProjectServiceImplSpec.kt의 물리 저장소 테스트들과 동일하게 @Value 기본값(설정 오버라이드가
    // 없는 test 프로파일)을 그대로 가정한다.
    private val gitDir = File("/tmp/yona/git/$ownerName/$projName.git")

    init {
        beforeTest {
            projectRepository.findByOwnerAndName(ownerName, projName).ifPresent { projectRepository.delete(it) }
            userRepository.findByLoginId(ownerName).ifPresent { userRepository.delete(it) }
            gitDir.deleteRecursively()
        }

        describe("ProjectServiceImpl.deleteProject — 물리 git 저장소 정리") {
            it("프로젝트를 삭제하면 물리 bare 저장소 디렉터리도 함께 삭제되고, 같은 owner/name으로 재생성해도 500이 나지 않아야 한다") {
                val creator = userRepository.save(User(loginId = ownerName, name = "물리삭제소유자", email = "$ownerName@example.com"))

                val created = projectService.createProject(
                    Project(owner = ownerName, name = projName, vcs = "GIT"),
                    creator
                )
                gitDir.exists() shouldBe true

                projectService.deleteProject(created.id!!)
                gitDir.exists() shouldBe false

                // RED였을 때는 FileAlreadyExistsException(500)이 여기서 발생했다.
                val recreated = projectService.createProject(
                    Project(owner = ownerName, name = projName, vcs = "GIT"),
                    creator
                )
                recreated.owner shouldBe ownerName
                recreated.name shouldBe projName
                gitDir.exists() shouldBe true

                projectService.deleteProject(recreated.id!!)
            }
        }
    }
}
