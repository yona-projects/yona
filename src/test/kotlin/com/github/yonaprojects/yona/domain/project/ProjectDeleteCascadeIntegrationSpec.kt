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
    private val titleHeadRepository: TitleHeadRepository,
    private val commitCommentRepository: com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository,
    private val jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate
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
                userRepository.findByLoginId("casc-applicant").ifPresent { userRepository.delete(it) }
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

            // v1.6 Project.java:112 `@OneToMany(cascade = REMOVE) codeComments` — 프로젝트를 지우면 커밋(코드) 댓글도 함께 지웠다.
            it("커밋 댓글이 있는 프로젝트를 삭제할 수 있고 댓글도 함께 지워진다") {
                val (owner, project) = newProject("casc-commitcomment")
                val saved = commitCommentRepository.save(
                    com.github.yonaprojects.yona.domain.pullrequest.CommitComment(
                        project = project, commitId = "abc123", contents = "c",
                        author = com.github.yonaprojects.yona.domain.user.UserIdent(owner)
                    )
                )
                projectService.deleteProject(project.id!!)
                projectRepository.findById(project.id!!).isPresent shouldBe false
                commitCommentRepository.findById(saved.id!!).isPresent shouldBe false
            }

            // v1.6 User.enrolledProjects(조인테이블 user_enrolled_project, cascade ALL)는 Ebean이 삭제 때 교차 행을 같이 지웠다.
            // next는 User 쪽이 소유하는 조인테이블이라 프로젝트 삭제로는 지워지지 않아 FK 위반이 난다.
            it("가입 신청 대기 사용자가 있는 프로젝트를 삭제할 수 있다") {
                val (_, project) = newProject("casc-enroll")
                val applicant = userRepository.save(User(loginId = "casc-applicant", name = "신청자", email = "casc-applicant@example.com"))
                applicant.enroll(project)
                userRepository.save(applicant)

                projectService.deleteProject(project.id!!)

                projectRepository.findById(project.id!!).isPresent shouldBe false
                // 트랜잭션 밖이라 지연 컬렉션(enrolledProjects)을 읽을 수 없으므로 조인 테이블 행을 직접 센다.
                jdbcTemplate.queryForObject("select count(*) from user_enrolled_project where project_id = ?", Long::class.java, project.id) shouldBe 0L
            }
        }
    }
}
