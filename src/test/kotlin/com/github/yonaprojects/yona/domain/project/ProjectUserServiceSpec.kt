package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@Transactional
class ProjectUserServiceSpec @Autowired constructor(
    private val projectUserService: ProjectUserService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val entityManager: EntityManager,
    private val watchService: WatchService
) : AbstractIntegrationTest() {

    init {
        describe("ProjectUserService 통합 테스트") {
            lateinit var manager: User
            lateinit var member1: User
            lateinit var applicant: User
            lateinit var project: Project
            lateinit var roleManager: Role
            lateinit var roleMember: Role

            beforeEach {
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
                roleRepository.deleteAll()

                // 역할 등록
                roleManager = roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                roleMember = roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))

                // 유저 등록
                manager = userRepository.save(User(loginId = "manager-user", name = "관리자", email = "manager@yona.io"))
                member1 = userRepository.save(User(loginId = "member-user", name = "멤버1", email = "member@yona.io"))
                applicant = userRepository.save(User(loginId = "apply-user", name = "신청자", email = "apply@yona.io"))

                // 프로젝트 등록 (소유자를 manager 유저로 지정)
                project = projectRepository.save(
                    Project(name = "auth-project", owner = "manager-user")
                )

                // 관리자 멤버 관계 등록
                projectUserRepository.save(ProjectUser(project = project, user = manager, role = roleManager))

                // yona NotificationEvent.java:1468-1477 getReceivers(Project) 대응 (P2-20) — 매니저가 [GL-models_NotificationEvent-100]
                // 가입요청/취소 알림을 받으려면 프로젝트를 감시(Watch) 중이어야 한다. 대부분의 시나리오는
                // "매니저가 감시 중"인 일반적인 경우를 검증하므로 기본으로 감시 상태를 만들어 둔다
                // (감시하지 않는 매니저는 알림을 못 받는다는 사실 자체는 별도 테스트로 검증).
                watchService.watch(manager, ResourceType.PROJECT, project.id.toString())
            }

            it("1. 가입 신청 및 대기 신청 정상 등록 검증") {
                // When - 가입 신청
                projectUserService.enroll(project.id!!, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                // Then
                val updatedApplicant = userRepository.findById(applicant.id!!).orElse(null)
                updatedApplicant.enrolledProjects.map { it.id } shouldContain project.id

                val updatedProject = projectRepository.findById(project.id!!).orElse(null)
                updatedProject.enrolledUsers.map { it.id } shouldContain applicant.id
            }

            it("2. 가입 신청 승인 검증") {
                // Given - 가입 신청
                projectUserService.enroll(project.id!!, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                // When - 가입 승인
                projectUserService.acceptMemberRequest(project.id!!, applicant.id!!, manager.id!!)
                entityManager.flush()
                entityManager.clear()

                // Then
                val projectMembers = projectUserRepository.findByProjectId(project.id!!)
                projectMembers.any { it.user.id == applicant.id && it.role.id == RoleType.MEMBER.roleType } shouldBe true

                val updatedApplicant = userRepository.findById(applicant.id!!).orElse(null)
                updatedApplicant.enrolledProjects.map { it.id } shouldNotContain project.id
            }

            it("3. 멤버 권한 변경 및 소유자 강등 금지 검증") {
                // Given - member1 등록
                projectUserRepository.save(ProjectUser(project = project, user = member1, role = roleMember))

                // When - member1 권한을 MANAGER로 승급
                projectUserService.updateMemberRole(project.id!!, member1.id!!, RoleType.MANAGER.roleType, manager.id!!)

                // Then
                val updatedMember = projectUserRepository.findByProjectIdAndUserId(project.id!!, member1.id!!).orElse(null)
                updatedMember shouldNotBe null
                updatedMember.role.id shouldBe RoleType.MANAGER.roleType

                // 소유자(owner) 강등 시도 시 예외 발생 검증
                shouldThrow<IllegalArgumentException> {
                    projectUserService.updateMemberRole(project.id!!, manager.id!!, RoleType.MEMBER.roleType, manager.id!!)
                }
            }

            it("4. 멤버 삭제 및 소유자 탈퇴 금지 검증") {
                // Given - member1 등록
                projectUserRepository.save(ProjectUser(project = project, user = member1, role = roleMember))

                // When - member1 삭제
                projectUserService.removeMember(project.id!!, member1.id!!, manager.id!!)

                // Then
                val memberExist = projectUserRepository.existsByProjectIdAndUserId(project.id!!, member1.id!!)
                memberExist shouldBe false

                // 소유자 탈퇴 시도 시 예외 발생 검증
                shouldThrow<IllegalArgumentException> {
                    projectUserService.removeMember(project.id!!, manager.id!!, manager.id!!)
                }
            }

            it("5. 이미 프로젝트 멤버인 유저가 가입 신청하면 예외가 발생해야 한다 (P1-16)") {
                // Given - member1은 이미 프로젝트 멤버
                projectUserRepository.save(ProjectUser(project = project, user = member1, role = roleMember))

                // When & Then
                shouldThrow<IllegalArgumentException> {
                    projectUserService.enroll(project.id!!, member1.id!!)
                }
            }

            it("6. 이미 대기 중인 가입 신청이 있는 유저가 재신청해도 알림이 중복 발행되지 않아야 한다 (P1-16)") {
                // Given - 최초 가입 신청
                projectUserService.enroll(project.id!!, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                // When - 동일 유저가 다시 가입 신청(중복)
                projectUserService.enroll(project.id!!, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                // Then - 대기 신청 목록엔 여전히 1건만 있고, 신청 알림도 1건만 발행돼야 한다
                val updatedApplicant = userRepository.findById(applicant.id!!).orElse(null)
                updatedApplicant.enrolledProjects.count { it.id == project.id } shouldBe 1

                val requestEvents = notificationEventRepository.findAll().filter {
                    it.resourceId == project.id.toString() &&
                        it.eventType == EventType.MEMBER_ENROLL_REQUEST &&
                        it.newValue == "REQUEST"
                }
                requestEvents.size shouldBe 1
            }

            // yona NotificationEvent.java:1468-1477 getReceivers(Project) 대응 (P2-20) — 프로젝트를 [GL-models_NotificationEvent-100]
            // 감시(Watch)하지 않는 매니저는 가입요청/취소 알림 수신자에서 빠져야 한다.
            it("8. 프로젝트를 감시하지 않는 매니저는 가입요청 알림을 받지 않아야 한다 (P2-20)") {
                watchService.unwatch(manager, ResourceType.PROJECT, project.id.toString())

                projectUserService.enroll(project.id!!, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                val requestEvents = notificationEventRepository.findAll().filter {
                    it.resourceId == project.id.toString() &&
                        it.eventType == EventType.MEMBER_ENROLL_REQUEST &&
                        it.newValue == "REQUEST"
                }
                requestEvents.size shouldBe 0
            }

            // yona EnrollProjectApp.java:55-71 대응 (P1-142, P1-123과 대칭인 신규 발견). 대기 중인 [GL-controllers_EnrollProjectApp-003]
            // 가입 신청이 실제로 없으면 취소 알림을 발행하지 않고, 이미 정식 멤버라면 취소 자체를
            // 거부해야 한다.
            it("7. 대기 중인 가입 신청이 없는 상태에서 취소를 호출하면 알림을 발행하지 않아야 한다") {
                val bystander = userRepository.save(User(loginId = "bystander-user", name = "구경꾼", email = "bystander@yona.io"))

                projectUserService.cancelEnroll(project.id!!, bystander.id!!)
                entityManager.flush()
                entityManager.clear()

                val cancelEvents = notificationEventRepository.findAll().filter {
                    it.resourceId == project.id.toString() && it.eventType == EventType.MEMBER_ENROLL_REQUEST && it.newValue == "CANCEL"
                }
                cancelEvents.size shouldBe 0
            }

            it("8. 이미 정식 멤버인 유저가 가입 신청 취소를 호출하면 예외가 발생해야 한다") {
                projectUserRepository.save(ProjectUser(project = project, user = member1, role = roleMember))

                shouldThrow<IllegalArgumentException> {
                    projectUserService.cancelEnroll(project.id!!, member1.id!!)
                }
            }

            it("9. 대기 중인 가입 신청을 취소하면 목록에서 제거되어야 한다") {
                projectUserService.enroll(project.id!!, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                projectUserService.cancelEnroll(project.id!!, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                val updatedApplicant = userRepository.findById(applicant.id!!).orElse(null)
                updatedApplicant.enrolledProjects.count { it.id == project.id } shouldBe 0

                // NotificationEventRecorder의 30초 draft-window 병합(A→B→A 상쇄, P1-27)에 따라
                // 즉시 신청→취소는 REQUEST/CANCEL 알림이 서로 상쇄되어 0건이 되는 게 legacy와
                // 일치하는 정상 동작이다(신청 직후 취소했는데 관리자에게 메일이 가는 게 오히려 결함).
                val events = notificationEventRepository.findAll().filter {
                    it.resourceId == project.id.toString() && it.eventType == EventType.MEMBER_ENROLL_REQUEST
                }
                events.size shouldBe 0
            }
            it("10. getProjectMembers 조회 검증") {
                projectUserService.getProjectMembers(project.id!!).size shouldBe 1
            }

            it("11. enroll 등 엔티티 조회 실패 시 예외 검증") {
                shouldThrow<IllegalArgumentException> {
                    projectUserService.enroll(-1L, applicant.id!!)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserService.enroll(project.id!!, -1L)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserService.cancelEnroll(-1L, applicant.id!!)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserService.cancelEnroll(project.id!!, -1L)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserService.acceptMemberRequest(-1L, applicant.id!!, manager.id!!)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserService.acceptMemberRequest(project.id!!, -1L, manager.id!!)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserService.rejectMemberRequest(-1L, applicant.id!!, manager.id!!)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserService.rejectMemberRequest(project.id!!, -1L, manager.id!!)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserService.addMember(-1L, applicant.loginId, manager.id!!)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserService.addMember(project.id!!, "not-found-login-id", manager.id!!)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserService.updateMemberRole(-1L, member1.id!!, RoleType.MANAGER.roleType, manager.id!!)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserService.updateMemberRole(project.id!!, -1L, RoleType.MANAGER.roleType, manager.id!!)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserService.updateMemberRole(project.id!!, applicant.id!!, RoleType.MANAGER.roleType, manager.id!!)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserRepository.save(ProjectUser(project = project, user = member1, role = roleMember))
                    projectUserService.updateMemberRole(project.id!!, member1.id!!, -1L, manager.id!!)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserService.removeMember(-1L, member1.id!!, manager.id!!)
                }
                shouldThrow<IllegalArgumentException> {
                    projectUserService.removeMember(project.id!!, -1L, manager.id!!)
                }
            }

            it("12. acceptMemberRequest - 이미 가입 완료된 유저라면 대기 신청 목록에서만 소거") {
                projectUserRepository.save(ProjectUser(project = project, user = applicant, role = roleMember))
                applicant.enroll(project)
                userRepository.save(applicant)
                entityManager.flush()
                
                projectUserService.acceptMemberRequest(project.id!!, applicant.id!!, manager.id!!)
                val updatedApplicant = userRepository.findById(applicant.id!!).orElse(null)
                updatedApplicant.enrolledProjects.map { it.id } shouldNotContain project.id
            }

            it("13. rejectMemberRequest 정상 동작 검증") {
                projectUserService.enroll(project.id!!, applicant.id!!)
                entityManager.flush()
                
                projectUserService.rejectMemberRequest(project.id!!, applicant.id!!, manager.id!!)
                val updatedApplicant = userRepository.findById(applicant.id!!).orElse(null)
                updatedApplicant.enrolledProjects.map { it.id } shouldNotContain project.id
            }

            it("14. addMember 정상 동작 및 이미 가입된 경우 예외 검증") {
                projectUserService.addMember(project.id!!, applicant.loginId, manager.id!!)
                projectUserRepository.existsByProjectIdAndUserId(project.id!!, applicant.id!!) shouldBe true

                shouldThrow<IllegalArgumentException> {
                    projectUserService.addMember(project.id!!, applicant.loginId, manager.id!!)
                }
            }

            // getProjectManagers()의 .filter { it.role.id == RoleType.MANAGER.roleType }는 기존
            // 테스트들이 전부 프로젝트에 매니저 1명만 있는 상태(beforeEach)로만 enroll/cancelEnroll을
            // 호출해서, "매니저가 아닌 멤버가 섞여 있어 필터링되는" false 쪽 분기가 비어 있었다.
            it("16. 매니저 아닌 멤버가 섞여 있어도 가입요청 알림은 매니저에게만 가야 한다") {
                projectUserRepository.save(ProjectUser(project = project, user = member1, role = roleMember))

                projectUserService.enroll(project.id!!, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                val requestEvents = notificationEventRepository.findAll().filter {
                    it.resourceId == project.id.toString() &&
                        it.eventType == EventType.MEMBER_ENROLL_REQUEST &&
                        it.newValue == "REQUEST"
                }
                requestEvents.size shouldBe 1
                requestEvents[0].receivers.map { it.id } shouldContain manager.id
                requestEvents[0].receivers.map { it.id } shouldNotContain member1.id
            }

            it("15. addMember - 대기 신청에 있던 유저라면 대기 목록에서 소거 검증") {
                applicant.enroll(project)
                userRepository.save(applicant)
                entityManager.flush()

                projectUserService.addMember(project.id!!, applicant.loginId, manager.id!!)
                val updatedApplicant = userRepository.findById(applicant.id!!).orElse(null)
                updatedApplicant.enrolledProjects.map { it.id } shouldNotContain project.id
            }

            it("17. acceptMemberRequest - MEMBER 역할이 존재하지 않으면 예외를 던져야 한다") {
                projectUserService.enroll(project.id!!, applicant.id!!)
                entityManager.flush()
                roleRepository.delete(roleMember)

                shouldThrow<IllegalStateException> {
                    projectUserService.acceptMemberRequest(project.id!!, applicant.id!!, manager.id!!)
                }
            }

            it("18. addMember - MEMBER 역할이 존재하지 않으면 예외를 던져야 한다") {
                roleRepository.delete(roleMember)

                shouldThrow<IllegalStateException> {
                    projectUserService.addMember(project.id!!, applicant.loginId, manager.id!!)
                }
            }
        }
    }
}
