package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.apitoken.ApiToken
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenRepository
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranch
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.deploykey.DeployKey
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyRepository
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.notification.UserProjectNotification
import com.github.yonaprojects.yona.domain.notification.UserProjectNotificationRepository
import com.github.yonaprojects.yona.domain.user.FavoriteService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.PushedBranch
import com.github.yonaprojects.yona.domain.vcs.PushedBranchRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

// v1.6 Project.delete()는 마일스톤·즐겨찾기·알림 설정·푸시 브랜치 등 프로젝트를 참조하는 모든 행을 정리한 뒤 프로젝트를 지웠다.
// next의 deleteProject()가 FK로 프로젝트를 참조하는 행을 빠뜨리면 삭제가 제약 위반으로 실패한다.
class ProjectDeleteCascadeIntegrationSpec @Autowired constructor(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectService: ProjectService,
    private val milestoneRepository: MilestoneRepository,
    private val pushedBranchRepository: PushedBranchRepository,
    private val userProjectNotificationRepository: UserProjectNotificationRepository,
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val deployKeyRepository: DeployKeyRepository,
    private val apiTokenRepository: ApiTokenRepository,
    private val favoriteService: FavoriteService,
    private val titleHeadRepository: TitleHeadRepository
) : AbstractIntegrationTest() {

    private val ownerName = "del-casc-owner"

    init {
        fun newProject(name: String): Pair<User, Project> {
            val owner = userRepository.findByLoginId(ownerName).orElseGet {
                userRepository.save(User(loginId = ownerName, name = "삭제캐스케이드", email = "$ownerName@example.com"))
            }
            val project = projectService.createProject(Project(owner = ownerName, name = name, vcs = "GIT"), owner)
            return owner to project
        }

        describe("ProjectServiceImpl.deleteProject — 프로젝트를 FK로 참조하는 행 정리") {
            afterEach {
                apiTokenRepository.deleteAll()
                userRepository.findByLoginId(ownerName).ifPresent { userRepository.delete(it) }
            }

            it("마일스톤이 있는 프로젝트를 삭제할 수 있다") {
                val (_, project) = newProject("casc-milestone")
                milestoneRepository.save(Milestone(title = "m1", project = project))
                projectService.deleteProject(project.id!!)
                projectRepository.findById(project.id!!).isPresent shouldBe false
            }

            it("푸시 브랜치 기록이 있는 프로젝트를 삭제할 수 있다") {
                val (_, project) = newProject("casc-pushed")
                pushedBranchRepository.save(PushedBranch(name = "feature", pushedDate = Instant.now(), project = project))
                projectService.deleteProject(project.id!!)
                projectRepository.findById(project.id!!).isPresent shouldBe false
            }

            it("프로젝트별 알림 설정이 있는 프로젝트를 삭제할 수 있다") {
                val (owner, project) = newProject("casc-notif")
                userProjectNotificationRepository.save(UserProjectNotification(user = owner, project = project, notificationType = EventType.NEW_ISSUE))
                projectService.deleteProject(project.id!!)
                projectRepository.findById(project.id!!).isPresent shouldBe false
            }

            it("즐겨찾기된 프로젝트를 삭제할 수 있다") {
                val (owner, project) = newProject("casc-fav")
                favoriteService.toggleFavoriteProject(owner.id!!, project.id!!)
                projectService.deleteProject(project.id!!)
                projectRepository.findById(project.id!!).isPresent shouldBe false
            }

            it("보호 브랜치 규칙이 있는 프로젝트를 삭제할 수 있다") {
                val (_, project) = newProject("casc-protected")
                protectedBranchRepository.save(ProtectedBranch(project = project, branchPattern = "main"))
                projectService.deleteProject(project.id!!)
                projectRepository.findById(project.id!!).isPresent shouldBe false
            }

            it("배포 키가 있는 프로젝트를 삭제할 수 있다") {
                val (_, project) = newProject("casc-deploykey")
                deployKeyRepository.save(DeployKey(project = project, title = "ci", publicKey = "ssh-ed25519 AAAA", fingerprint = "fp-casc"))
                projectService.deleteProject(project.id!!)
                projectRepository.findById(project.id!!).isPresent shouldBe false
            }

            it("API 토큰의 선택 저장소 범위에 포함된 프로젝트를 삭제할 수 있다") {
                val (owner, project) = newProject("casc-apitoken")
                apiTokenRepository.save(ApiToken(owner = owner, name = "t", tokenHash = "hash-casc", scopedProjects = mutableSetOf(project), expiresAt = Instant.now().plusSeconds(3600)))
                projectService.deleteProject(project.id!!)
                projectRepository.findById(project.id!!).isPresent shouldBe false
            }
        }
    }
}
