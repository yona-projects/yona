package com.github.yonaprojects.yona.domain.organization

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.FavoriteOrganization
import com.github.yonaprojects.yona.domain.user.FavoriteOrganizationRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@Transactional
class OrganizationServiceSpec @Autowired constructor(
    private val organizationService: OrganizationService,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val projectRepository: ProjectRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val entityManager: EntityManager,
    private val favoriteOrganizationRepository: FavoriteOrganizationRepository
) : AbstractIntegrationTest() {

    init {
        describe("OrganizationService 통합 테스트") {
            lateinit var admin: User
            lateinit var member: User
            lateinit var roleAdmin: Role
            lateinit var roleMember: Role

            beforeEach {
                favoriteOrganizationRepository.deleteAll()
                projectRepository.deleteAll()
                organizationUserRepository.deleteAll()
                organizationRepository.deleteAll()
                userRepository.deleteAll()
                roleRepository.deleteAll()

                roleAdmin = roleRepository.save(Role(id = RoleType.ORG_ADMIN.roleType, name = "ORG_ADMIN"))
                roleMember = roleRepository.save(Role(id = RoleType.ORG_MEMBER.roleType, name = "ORG_MEMBER"))

                admin = userRepository.save(User(loginId = "admin-user", name = "조직관리자", email = "admin@yona.io"))
                member = userRepository.save(User(loginId = "member-user", name = "조직원", email = "member@yona.io"))
            }

            it("1. 조직 생성 및 초기 관리자 지정 검증") {
                // When - 조직 생성
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)

                // Then
                org.id shouldNotBe null
                org.name shouldBe "my-org"
                org.descr shouldBe "설명"

                val orgUser = organizationUserRepository.findByOrganizationIdAndUserId(org.id!!, admin.id!!).orElse(null)
                orgUser shouldNotBe null
                orgUser.role.id shouldBe RoleType.ORG_ADMIN.roleType
            }

            it("예약어를 조직 이름으로 사용하려 하면 예외가 발생해야 한다(P2-01)") {
                shouldThrow<IllegalArgumentException> {
                    organizationService.createOrganization("projects", "설명", admin.id!!)
                }
            }

            // yona models/Organization.java:42 @Constraints.Pattern(User.LOGIN_ID_PATTERN) 대응 (P1-108).
            it("형식에 맞지 않는(공백 포함) 조직 이름이면 예외가 발생해야 한다") {
                shouldThrow<IllegalArgumentException> {
                    organizationService.createOrganization("my org", "설명", admin.id!!)
                }
            }

            it("형식에 맞는(영문/숫자/하이픈) 조직 이름은 정상 생성되어야 한다") {
                val org = organizationService.createOrganization("my-valid-org123", "설명", admin.id!!)
                org.name shouldBe "my-valid-org123"
            }

            it("2. 조직 멤버 추가 검증") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)

                // When - 멤버 추가
                organizationService.addOrganizationMember(org.id!!, member.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)

                // Then
                val exists = organizationUserRepository.existsByOrganizationIdAndUserId(org.id!!, member.id!!)
                exists shouldBe true

                val orgUser = organizationUserRepository.findByOrganizationIdAndUserId(org.id!!, member.id!!).orElse(null)
                orgUser.role.id shouldBe RoleType.ORG_MEMBER.roleType
            }

            it("3. 조직 멤버 권한 변경 및 마지막 관리자 강등 제한 검증") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)
                organizationService.addOrganizationMember(org.id!!, member.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)

                // 마지막 관리자(ORG_ADMIN) 강등 시도 시 예외 발생 검증
                shouldThrow<IllegalArgumentException> {
                    organizationService.updateOrganizationMemberRole(org.id!!, admin.id!!, RoleType.ORG_MEMBER.roleType, admin.id!!)
                }

                // member를 관리자로 승격
                organizationService.updateOrganizationMemberRole(org.id!!, member.id!!, RoleType.ORG_ADMIN.roleType, admin.id!!)

                // 이제 admin은 마지막 관리자가 아니므로 강등 성공해야 함
                organizationService.updateOrganizationMemberRole(org.id!!, admin.id!!, RoleType.ORG_MEMBER.roleType, admin.id!!)

                val updatedAdmin = organizationUserRepository.findByOrganizationIdAndUserId(org.id!!, admin.id!!).orElse(null)
                updatedAdmin.role.id shouldBe RoleType.ORG_MEMBER.roleType
            }

            it("4. 조직 멤버 삭제 및 마지막 관리자 탈퇴 제한 검증") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)
                organizationService.addOrganizationMember(org.id!!, member.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)

                // 마지막 관리자 삭제 시도 시 예외 발생 검증
                shouldThrow<IllegalArgumentException> {
                    organizationService.removeOrganizationMember(org.id!!, admin.id!!, admin.id!!)
                }

                // 일반 멤버 삭제 시 정상 삭제되어야 함
                organizationService.removeOrganizationMember(org.id!!, member.id!!, admin.id!!)
                organizationUserRepository.existsByOrganizationIdAndUserId(org.id!!, member.id!!) shouldBe false
            }

            it("5. 조직 삭제 제약(하위 프로젝트 존재 여부) 검증") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)

                // 1) 하위 프로젝트가 존재할 때 삭제 시도 -> 실패 예외 발생해야 함
                val project = projectRepository.save(
                    Project(name = "org-project", owner = "admin-user", organization = org)
                )
                org.projects.add(project)
                organizationRepository.save(org)

                shouldThrow<IllegalArgumentException> {
                    organizationService.deleteOrganization(org.id!!, admin.id!!)
                }

                // 2) 하위 프로젝트 제거 후 삭제 시도 -> 성공해야 함
                org.projects.clear()
                organizationRepository.save(org)
                projectRepository.delete(project)

                organizationService.deleteOrganization(org.id!!, admin.id!!)
                organizationRepository.findById(org.id!!).isPresent shouldBe false
            }

            it("6. 게스트 계정은 조직 멤버로 추가할 수 없어야 한다 (P1-17)") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)
                val guest = userRepository.save(
                    User(loginId = "guest-user", name = "게스트", email = "guest@yona.io", isGuest = true)
                )

                shouldThrow<IllegalArgumentException> {
                    organizationService.addOrganizationMember(org.id!!, guest.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)
                }

                organizationUserRepository.existsByOrganizationIdAndUserId(org.id!!, guest.id!!) shouldBe false
            }

            // yona EnrollOrganizationApp.java 대응, Project P1-16과 동일 유형(P1-122). 이미 대기 중인
            // 가입 신청이 있는 유저가 재신청해도 알림이 중복 발행되지 않아야 한다.
            it("7. 이미 대기 중인 가입 신청이 있는 유저가 재신청해도 알림이 중복 발행되지 않아야 한다 (P1-122)") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)
                val applicant = userRepository.save(
                    User(loginId = "applicant-user", name = "신청자", email = "applicant@yona.io")
                )

                // Given - 최초 가입 신청
                organizationService.enroll(org.name, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                // When - 동일 유저가 다시 가입 신청(중복)
                organizationService.enroll(org.name, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                // Then - 대기 신청 목록엔 여전히 1건만 있고, 신청 알림도 1건만 발행돼야 한다
                val updatedApplicant = userRepository.findById(applicant.id!!).orElse(null)
                updatedApplicant.enrolledOrganizations.count { it.id == org.id } shouldBe 1

                val requestEvents = notificationEventRepository.findAll().filter {
                    it.resourceId == org.id.toString() &&
                        it.eventType == EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST &&
                        it.newValue == "REQUEST"
                }
                requestEvents.size shouldBe 1
            }

            // yona EnrollOrganizationApp.java:82,101-104 대응 (P1-123). 대기 중인 가입 신청이 [GL-controllers_EnrollOrganizationApp-005]
            // 실제로 없으면 취소 알림을 발행하지 않고, 이미 정식 멤버라면 취소 자체를 거부해야 한다.
            it("8. 대기 중인 가입 신청이 없는 상태에서 취소를 호출하면 알림을 발행하지 않아야 한다") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)
                val bystander = userRepository.save(
                    User(loginId = "bystander-user", name = "구경꾼", email = "bystander@yona.io")
                )

                organizationService.cancelEnroll(org.name, bystander.id!!)
                entityManager.flush()
                entityManager.clear()

                val cancelEvents = notificationEventRepository.findAll().filter {
                    it.resourceId == org.id.toString() && it.eventType == EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST && it.newValue == "CANCEL"
                }
                cancelEvents.size shouldBe 0
            }

            it("9. 이미 정식 멤버인 유저가 가입 신청 취소를 호출하면 예외가 발생해야 한다") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)
                organizationService.addOrganizationMember(org.id!!, member.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)

                shouldThrow<IllegalArgumentException> {
                    organizationService.cancelEnroll(org.name, member.id!!)
                }
            }

            // yona NotificationEvent.java:1257-1286 afterOrganizationMemberRequest()의 REQUEST<->CANCEL
            // oldValue/newValue 페어링 대응 (P2-21). 신청 직후 짧은 시간(30초 draft window) 안에
            // 취소하면 신청/취소 알림 두 건 모두 상쇄(draft 병합)되어 관리자에게 아무 알림도 발행되지
            // 않아야 한다 — oldValue/newValue가 REQUEST<->CANCEL로 대칭 페어링되어 있어야만 병합 로직
            // (NotificationEventRecorder.record())이 두 이벤트를 서로의 반대값으로 인식해 상쇄시킨다.
            it("10. 대기 중인 가입 신청을 곧바로 취소하면 신청/취소 알림이 모두 상쇄되어 발행되지 않아야 한다 (P2-21)") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)
                val applicant = userRepository.save(
                    User(loginId = "applicant-user2", name = "신청자2", email = "applicant2@yona.io")
                )
                organizationService.enroll(org.name, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                organizationService.cancelEnroll(org.name, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                val updatedApplicant = userRepository.findById(applicant.id!!).orElse(null)
                updatedApplicant.enrolledOrganizations.count { it.id == org.id } shouldBe 0

                val requestOrCancelEvents = notificationEventRepository.findAll().filter {
                    it.resourceId == org.id.toString() && it.eventType == EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST
                }
                requestOrCancelEvents.size shouldBe 0
            }

            // yona FavoriteOrganization.java:38-46 updateFavoriteOrganization() 대응 (P2-19). [GL-models_FavoriteOrganization-007]
            it("11. 조직명을 변경하면 그 조직을 즐겨찾기한 모든 사용자의 비정규화된 organizationName도 갱신돼야 한다") {
                val org = organizationService.createOrganization("old-org-name", "설명", admin.id!!)
                val favoriter = userRepository.save(
                    User(loginId = "favoriter-user", name = "즐겨찾기유저", email = "favoriter@yona.io")
                )
                val favorite = favoriteOrganizationRepository.save(FavoriteOrganization(user = favoriter, organization = org))
                favorite.organizationName shouldBe "old-org-name"

                organizationService.updateOrganizationSettings(org.id!!, "new-org-name", "설명", admin.id!!)

                val updatedFavorite = favoriteOrganizationRepository.findById(favorite.id!!).orElseThrow()
                updatedFavorite.organizationName shouldBe "new-org-name"
            }
            describe("추가 커버리지 테스트") {
                it("findByName 조회 검증") {
                    val org = organizationService.createOrganization("find-org", "desc", admin.id!!)
                    organizationService.findByName("find-org")?.id shouldBe org.id
                    organizationService.findByName("not-exist-org") shouldBe null
                }
                
                it("updateOrganizationSettings에서 이미 존재하는 이름으로 변경 시 예외") {
                    organizationService.createOrganization("org-1", "desc1", admin.id!!)
                    val org2 = organizationService.createOrganization("org-2", "desc2", admin.id!!)
                    shouldThrow<IllegalArgumentException> {
                        organizationService.updateOrganizationSettings(org2.id!!, "org-1", "new desc", admin.id!!)
                    }
                    shouldThrow<IllegalArgumentException> {
                        organizationService.updateOrganizationSettings(org2.id!!, admin.loginId, "new desc", admin.id!!)
                    }
                }
                
                it("addOrganizationMember 이미 멤버인 유저 및 enroll 취소 검증") {
                    val org = organizationService.createOrganization("org-member-test", "desc", admin.id!!)
                    val targetUser = userRepository.save(User(loginId = "target-user", name = "타겟", email = "target@yona.io"))
                    
                    targetUser.enroll(org)
                    userRepository.save(targetUser)
                    
                    organizationService.addOrganizationMember(org.id!!, targetUser.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)
                    
                    shouldThrow<IllegalArgumentException> {
                        organizationService.addOrganizationMember(org.id!!, targetUser.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)
                    }
                    val reloadedUser = userRepository.findById(targetUser.id!!).get()
                    reloadedUser.enrolledOrganizations.contains(org) shouldBe false
                }
                
                it("removeOrganizationMember 멤버가 없는 경우 예외") {
                    val org = organizationService.createOrganization("org-remove-test", "desc", admin.id!!)
                    val targetUser = userRepository.save(User(loginId = "target-user2", name = "타겟2", email = "target2@yona.io"))
                    shouldThrow<IllegalArgumentException> {
                        organizationService.removeOrganizationMember(org.id!!, targetUser.id!!, admin.id!!)
                    }
                }
                
                it("updateOrganizationMemberRole 관리자가 여러명이면 마지막 관리자가 아니므로 강등 가능") {
                    val org = organizationService.createOrganization("org-role-test", "desc", admin.id!!)
                    val targetUser = userRepository.save(User(loginId = "target-admin", name = "관리자2", email = "admin2@yona.io"))
                    organizationService.addOrganizationMember(org.id!!, targetUser.loginId, RoleType.ORG_ADMIN.roleType, admin.id!!)
                    
                    organizationService.updateOrganizationMemberRole(org.id!!, admin.id!!, RoleType.ORG_MEMBER.roleType, admin.id!!)
                    val updatedAdmin = organizationUserRepository.findByOrganizationIdAndUserId(org.id!!, admin.id!!).get()
                    updatedAdmin.role.id shouldBe RoleType.ORG_MEMBER.roleType
                }
                
                it("enroll 시 이미 대기중인 경우 조용히 리턴") {
                    val org = organizationService.createOrganization("org-enroll-test", "desc", admin.id!!)
                    val targetUser = userRepository.save(User(loginId = "enroll-user", name = "대기자", email = "enroll@yona.io"))
                    
                    organizationService.enroll(org.name, targetUser.id!!)
                    organizationService.enroll(org.name, targetUser.id!!) // should return silently
                }
                
                it("enroll 시 관리자가 없는 경우 알림 전송 안함") {
                    val org = organizationService.createOrganization("org-noadmin-test", "desc", admin.id!!)
                    organizationUserRepository.deleteAll() // 강제로 관리자 없앰
                    val targetUser = userRepository.save(User(loginId = "enroll-user2", name = "대기자2", email = "enroll2@yona.io"))
                    organizationService.enroll(org.name, targetUser.id!!) // doesn't crash
                }
                
                it("leaveOrganization 관리자가 탈퇴") {
                    val org = organizationService.createOrganization("org-leave-test", "desc", admin.id!!)
                    organizationService.leaveOrganization(org.id!!, admin.id!!)
                    organizationUserRepository.existsByOrganizationIdAndUserId(org.id!!, admin.id!!) shouldBe false
                }
                
                it("leaveOrganization 일반 멤버가 관리자가 1명일때 탈퇴 실패") {
                    val org = organizationService.createOrganization("org-leave-fail", "desc", admin.id!!)
                    val targetUser = userRepository.save(User(loginId = "leave-user", name = "탈퇴자", email = "leave@yona.io"))
                    organizationService.addOrganizationMember(org.id!!, targetUser.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)
                    
                    shouldThrow<IllegalStateException> {
                        organizationService.leaveOrganization(org.id!!, targetUser.id!!)
                    }
                }
                
                it("createOrganization(organization: Organization) 1-인자 오버로드는 created만 채워 그대로 저장해야 한다") {
                    // 3-인자 오버로드(name/descr/creatorId)와 달리 이 오버로드는 이름 검증·관리자 지정 없이
                    // Organization 엔티티를 그대로 저장만 한다 — 어떤 테스트도 이 오버로드를 호출한 적이 없었음.
                    val organization = Organization(name = "raw-created-org", descr = "설명")
                    organization.created shouldBe null

                    val saved = organizationService.createOrganization(organization)

                    saved.id shouldNotBe null
                    saved.name shouldBe "raw-created-org"
                    saved.created shouldNotBe null
                    organizationUserRepository.existsByOrganizationIdAndUserId(saved.id!!, admin.id!!) shouldBe false
                }

                it("leaveOrganization 일반 멤버가 관리자가 2명일때 탈퇴 성공") {
                    val org = organizationService.createOrganization("org-leave-success", "desc", admin.id!!)
                    val admin2 = userRepository.save(User(loginId = "admin-2", name = "어드민2", email = "admin2@yona.io"))
                    organizationService.addOrganizationMember(org.id!!, admin2.loginId, RoleType.ORG_ADMIN.roleType, admin.id!!)
                    
                    val targetUser = userRepository.save(User(loginId = "leave-user2", name = "탈퇴자2", email = "leave2@yona.io"))
                    organizationService.addOrganizationMember(org.id!!, targetUser.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)
                    
                    organizationService.leaveOrganization(org.id!!, targetUser.id!!)
                    organizationUserRepository.existsByOrganizationIdAndUserId(org.id!!, targetUser.id!!) shouldBe false
                }
            }

            describe("존재하지 않는 리소스 조회 시 예외 처리 (orElseThrow 분기)") {
                it("createOrganization - 존재하지 않는 creatorId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        organizationService.createOrganization("no-creator-org", "desc", 999999L)
                    }
                }

                it("createOrganization - ORG_ADMIN 역할이 존재하지 않으면 예외를 던져야 한다") {
                    organizationUserRepository.deleteAll()
                    roleRepository.deleteAll()
                    shouldThrow<IllegalStateException> {
                        organizationService.createOrganization("no-role-org", "desc", admin.id!!)
                    }
                }

                it("updateOrganizationSettings - 존재하지 않는 orgId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        organizationService.updateOrganizationSettings(999999L, "new-name", "desc", admin.id!!)
                    }
                }

                it("addOrganizationMember - 존재하지 않는 orgId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        organizationService.addOrganizationMember(999999L, member.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)
                    }
                }

                it("addOrganizationMember - 존재하지 않는 userLoginId면 예외를 던져야 한다") {
                    val org = organizationService.createOrganization("org-no-user", "desc", admin.id!!)
                    shouldThrow<IllegalArgumentException> {
                        organizationService.addOrganizationMember(org.id!!, "no-such-login-id", RoleType.ORG_MEMBER.roleType, admin.id!!)
                    }
                }

                it("addOrganizationMember - 존재하지 않는 roleId면 예외를 던져야 한다") {
                    val org = organizationService.createOrganization("org-no-role", "desc", admin.id!!)
                    shouldThrow<IllegalArgumentException> {
                        organizationService.addOrganizationMember(org.id!!, member.loginId, 999999L, admin.id!!)
                    }
                }

                it("removeOrganizationMember - 존재하지 않는 orgId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        organizationService.removeOrganizationMember(999999L, member.id!!, admin.id!!)
                    }
                }

                it("updateOrganizationMemberRole - 존재하지 않는 orgId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        organizationService.updateOrganizationMemberRole(999999L, member.id!!, RoleType.ORG_MEMBER.roleType, admin.id!!)
                    }
                }

                it("updateOrganizationMemberRole - 대상 유저가 조직 멤버가 아니면 예외를 던져야 한다") {
                    val org = organizationService.createOrganization("org-role-nomember", "desc", admin.id!!)
                    shouldThrow<IllegalArgumentException> {
                        organizationService.updateOrganizationMemberRole(org.id!!, member.id!!, RoleType.ORG_MEMBER.roleType, admin.id!!)
                    }
                }

                it("updateOrganizationMemberRole - 존재하지 않는 newRoleId면 예외를 던져야 한다") {
                    val org = organizationService.createOrganization("org-role-norole", "desc", admin.id!!)
                    shouldThrow<IllegalArgumentException> {
                        organizationService.updateOrganizationMemberRole(org.id!!, admin.id!!, 999999L, admin.id!!)
                    }
                }

                it("deleteOrganization - 존재하지 않는 orgId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        organizationService.deleteOrganization(999999L, admin.id!!)
                    }
                }

                it("enroll - 존재하지 않는 orgName이면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        organizationService.enroll("no-such-org", member.id!!)
                    }
                }

                it("enroll - 존재하지 않는 userId면 예외를 던져야 한다") {
                    val org = organizationService.createOrganization("org-enroll-nouser", "desc", admin.id!!)
                    shouldThrow<IllegalArgumentException> {
                        organizationService.enroll(org.name, 999999L)
                    }
                }

                it("cancelEnroll - 존재하지 않는 orgName이면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        organizationService.cancelEnroll("no-such-org", member.id!!)
                    }
                }

                it("cancelEnroll - 존재하지 않는 userId면 예외를 던져야 한다") {
                    val org = organizationService.createOrganization("org-cancel-nouser", "desc", admin.id!!)
                    shouldThrow<IllegalArgumentException> {
                        organizationService.cancelEnroll(org.name, 999999L)
                    }
                }

                it("leaveOrganization - 존재하지 않는 orgId면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        organizationService.leaveOrganization(999999L, admin.id!!)
                    }
                }
            }

            describe("추가 분기 커버리지") {
                it("createOrganization - 이미 존재하는 조직 이름이면 예외를 던져야 한다") {
                    organizationService.createOrganization("dup-org", "desc", admin.id!!)
                    shouldThrow<IllegalArgumentException> {
                        organizationService.createOrganization("dup-org", "desc2", admin.id!!)
                    }
                }

                it("createOrganization - 이미 존재하는 사용자 loginId와 같은 이름이면 예외를 던져야 한다") {
                    shouldThrow<IllegalArgumentException> {
                        organizationService.createOrganization(admin.loginId, "desc", admin.id!!)
                    }
                }

                it("updateOrganizationSettings - 이름을 바꾸지 않으면(동일 이름) 중복 이름 검사를 건너뛰어야 한다") {
                    val org = organizationService.createOrganization("org-samename", "desc", admin.id!!)
                    organizationService.updateOrganizationSettings(org.id!!, "org-samename", "새 설명", admin.id!!)
                    organizationRepository.findById(org.id!!).get().descr shouldBe "새 설명"
                }

                it("removeOrganizationMember - 관리자가 2명이면 관리자를 삭제해도 예외가 발생하지 않아야 한다") {
                    val org = organizationService.createOrganization("org-remove-admin2", "desc", admin.id!!)
                    val admin2 = userRepository.save(User(loginId = "remove-admin2", name = "어드민2", email = "remove-admin2@yona.io"))
                    organizationService.addOrganizationMember(org.id!!, admin2.loginId, RoleType.ORG_ADMIN.roleType, admin.id!!)

                    organizationService.removeOrganizationMember(org.id!!, admin.id!!, admin2.id!!)

                    organizationUserRepository.existsByOrganizationIdAndUserId(org.id!!, admin.id!!) shouldBe false
                }

                it("enroll - 이미 정식 멤버인 유저가 enroll을 호출하면 예외를 던져야 한다") {
                    val org = organizationService.createOrganization("org-enroll-alreadymember", "desc", admin.id!!)
                    organizationService.addOrganizationMember(org.id!!, member.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)

                    shouldThrow<IllegalArgumentException> {
                        organizationService.enroll(org.name, member.id!!)
                    }
                }

                it("cancelEnroll 시 관리자가 없는 경우 알림 전송 없이 정상 취소되어야 한다") {
                    val org = organizationService.createOrganization("org-cancel-noadmin", "desc", admin.id!!)
                    val applicant = userRepository.save(User(loginId = "cancel-noadmin-user", name = "신청자", email = "cancel-noadmin@yona.io"))
                    organizationService.enroll(org.name, applicant.id!!)
                    entityManager.flush()
                    entityManager.clear()
                    organizationUserRepository.deleteAll()

                    organizationService.cancelEnroll(org.name, applicant.id!!)

                    val updatedApplicant = userRepository.findById(applicant.id!!).orElse(null)
                    updatedApplicant.enrolledOrganizations.count { it.id == org.id } shouldBe 0
                }

                it("leaveOrganization - 조직 멤버가 아닌 유저가 탈퇴를 시도해도(관리자가 2명 이상이면) 정상 처리돼야 한다") {
                    val org = organizationService.createOrganization("org-leave-notmember", "desc", admin.id!!)
                    val admin2 = userRepository.save(User(loginId = "leave-notmember-admin2", name = "어드민2", email = "leave-notmember-admin2@yona.io"))
                    organizationService.addOrganizationMember(org.id!!, admin2.loginId, RoleType.ORG_ADMIN.roleType, admin.id!!)
                    val outsider = userRepository.save(User(loginId = "leave-outsider", name = "비회원", email = "leave-outsider@yona.io"))

                    organizationService.leaveOrganization(org.id!!, outsider.id!!)

                    organizationUserRepository.existsByOrganizationIdAndUserId(org.id!!, outsider.id!!) shouldBe false
                }
            }
        }
    }
}
