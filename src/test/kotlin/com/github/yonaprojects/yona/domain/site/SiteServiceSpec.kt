package com.github.yonaprojects.yona.domain.site

import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.RecentIssueService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.PasswordEncodingService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

class SiteServiceSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val projectService = mockk<ProjectService>()
    val recentIssueService = mockk<RecentIssueService>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val passwordEncodingService = PasswordEncodingService()

    val service = SiteService(userRepository, projectRepository, projectUserRepository, projectService, recentIssueService, attachmentRepository, passwordEncodingService)

    val targetUser = User(id = 10L, loginId = "gildong", name = "홍길동", state = UserState.ACTIVE)

    beforeTest {
        clearMocks(userRepository, projectRepository, projectUserRepository, projectService, recentIssueService, attachmentRepository, answers = false)
    }

    describe("SiteService.toggleAccountLock") {
        it("대상 사용자가 없으면 아무 것도 하지 않아야 한다") {
            every { userRepository.findByLoginId("nobody") } returns Optional.empty()

            service.toggleAccountLock("nobody")

            verify(exactly = 0) { userRepository.save(any()) }
        }

        it("ACTIVE 상태 사용자는 LOCKED로 전환되어야 한다") {
            val user = User(id = 20L, loginId = "active-user", name = "활성사용자", state = UserState.ACTIVE)
            every { userRepository.findByLoginId("active-user") } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            service.toggleAccountLock("active-user")

            user.state shouldBe UserState.LOCKED
            verify(exactly = 1) { userRepository.save(user) }
        }

        it("LOCKED 상태 사용자는 ACTIVE로 전환되어야 한다") {
            val user = User(id = 21L, loginId = "locked-user", name = "잠긴사용자", state = UserState.LOCKED)
            every { userRepository.findByLoginId("locked-user") } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            service.toggleAccountLock("locked-user")

            user.state shouldBe UserState.ACTIVE
            verify(exactly = 1) { userRepository.save(user) }
        }
    }

    describe("SiteService.toggleGuestMode") {
        it("대상 사용자가 없으면 아무 것도 하지 않아야 한다") {
            every { userRepository.findByLoginId("nobody") } returns Optional.empty()

            service.toggleGuestMode("nobody")

            verify(exactly = 0) { userRepository.save(any()) }
        }

        it("게스트가 아닌 사용자는 게스트 모드로 전환되어야 한다") {
            val user = User(id = 22L, loginId = "normal-user", name = "일반사용자", isGuest = false)
            every { userRepository.findByLoginId("normal-user") } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            service.toggleGuestMode("normal-user")

            user.isGuest shouldBe true
            verify(exactly = 1) { userRepository.save(user) }
        }

        it("게스트인 사용자는 게스트 모드가 해제되어야 한다") {
            val user = User(id = 23L, loginId = "guest-user", name = "게스트사용자", isGuest = true)
            every { userRepository.findByLoginId("guest-user") } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            service.toggleGuestMode("guest-user")

            user.isGuest shouldBe false
            verify(exactly = 1) { userRepository.save(user) }
        }
    }

    describe("SiteService.toggleSiteAdminRole") {
        it("대상 사용자가 없으면 아무 것도 하지 않아야 한다") {
            every { userRepository.findByLoginId("nobody") } returns Optional.empty()

            service.toggleSiteAdminRole("nobody")

            verify(exactly = 0) { userRepository.save(any()) }
        }

        it("ACTIVE 상태 사용자는 SITE_ADMIN으로 전환되어야 한다") {
            val user = User(id = 24L, loginId = "will-be-admin", name = "일반사용자", state = UserState.ACTIVE)
            every { userRepository.findByLoginId("will-be-admin") } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            service.toggleSiteAdminRole("will-be-admin")

            user.state shouldBe UserState.SITE_ADMIN
            verify(exactly = 1) { userRepository.save(user) }
        }

        it("SITE_ADMIN 상태 사용자는 ACTIVE로 강등되어야 한다") {
            val user = User(id = 25L, loginId = "was-admin", name = "관리자", state = UserState.SITE_ADMIN)
            every { userRepository.findByLoginId("was-admin") } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            service.toggleSiteAdminRole("was-admin")

            user.state shouldBe UserState.ACTIVE
            verify(exactly = 1) { userRepository.save(user) }
        }
    }

    describe("SiteService.resetUserPassword / hashPassword") {
        it("대상 사용자를 찾을 수 없으면 예외가 발생해야 한다") {
            every { userRepository.findByLoginId("nobody") } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.resetUserPassword("nobody")
            }
        }

        it("비밀번호를 재설정하면 새 임시 비밀번호(6자)를 반환하고 Argon2 해시가 저장되어야 한다") {
            val user = User(id = 26L, loginId = "reset-target", name = "대상자")
            every { userRepository.findByLoginId("reset-target") } returns Optional.of(user)
            every { userRepository.save(any()) } answers { firstArg() }

            val newPassword = service.resetUserPassword("reset-target")

            newPassword.length shouldBe 6
            user.passwordSalt shouldBe null
            passwordEncodingService.matches(newPassword, user.password, null) shouldBe true
            verify(exactly = 1) { userRepository.save(user) }
        }
    }

    describe("SiteService.deleteUser (P1-41)") {
        it("사용자를 삭제하면 최근 방문 이력(RecentIssue)도 함께 정리해야 한다") {
            every { projectUserRepository.findByUserId(10L) } returns emptyList()
            every { projectUserRepository.deleteAll(any<List<ProjectUser>>()) } returns Unit
            every { userRepository.findById(10L) } returns Optional.of(targetUser)
            every { userRepository.save(any()) } answers { firstArg() }
            every { recentIssueService.deleteAll(targetUser) } returns Unit

            service.deleteUser(10L)

            targetUser.state shouldBe UserState.DELETED
            verify(exactly = 1) { recentIssueService.deleteAll(targetUser) }
        }

        it("삭제 대상이 프로젝트의 유일한 매니저이면 ONLY_MANAGER 예외가 발생해야 한다") {
            val role = Role(id = RoleType.MANAGER.roleType, name = "manager")
            val user = User(id = 30L, loginId = "sole-manager", name = "유일매니저")
            val project = Project(id = 100L, name = "proj")
            val pu = ProjectUser(id = 1L, user = user, project = project, role = role)
            every { projectUserRepository.findByUserId(30L) } returns listOf(pu)
            every { projectUserRepository.findByProjectId(100L) } returns listOf(pu)

            shouldThrow<IllegalStateException> {
                service.deleteUser(30L)
            }

            verify(exactly = 0) { userRepository.findById(any()) }
        }

        it("유일 매니저가 아니지만 대상 사용자를 찾을 수 없으면 USER_NOT_FOUND 예외가 발생해야 한다") {
            every { projectUserRepository.findByUserId(31L) } returns emptyList()
            every { userRepository.findById(31L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.deleteUser(31L)
            }
        }
    }

    describe("SiteService.deleteProject") {
        it("projectService.deleteProject로 위임해야 한다") {
            every { projectService.deleteProject(200L) } returns Unit

            service.deleteProject(200L)

            verify(exactly = 1) { projectService.deleteProject(200L) }
        }
    }

    describe("SiteService.isOnlyManager") {
        it("사용자가 어떤 프로젝트에도 소속되어 있지 않으면 false를 반환해야 한다") {
            every { projectUserRepository.findByUserId(40L) } returns emptyList()

            service.isOnlyManager(40L) shouldBe false
        }

        it("사용자가 소속은 있지만 MANAGER 역할이 아니면 false를 반환해야 한다") {
            val memberRole = Role(id = RoleType.MEMBER.roleType, name = "member")
            // role.id가 null인 데이터(깨진 역할 연결)도 MANAGER로 오인되지 않아야 한다(role.id nullable 분기 커버)
            val nullIdRole = Role(id = null, name = "broken-role")
            val user = User(id = 41L, loginId = "member-user", name = "멤버")
            val project = Project(id = 101L, name = "proj101")
            val pu = ProjectUser(id = 2L, user = user, project = project, role = memberRole)
            val puNullRole = ProjectUser(id = 12L, user = user, project = project, role = nullIdRole)
            every { projectUserRepository.findByUserId(41L) } returns listOf(pu, puNullRole)

            service.isOnlyManager(41L) shouldBe false
        }

        it("MANAGER인 프로젝트의 매니저가 자기 자신 1명뿐이면 true를 반환해야 한다") {
            val managerRole = Role(id = RoleType.MANAGER.roleType, name = "manager")
            val user = User(id = 42L, loginId = "sole-manager2", name = "유일매니저2")
            val project = Project(id = 102L, name = "proj102")
            val pu = ProjectUser(id = 3L, user = user, project = project, role = managerRole)
            every { projectUserRepository.findByUserId(42L) } returns listOf(pu)
            every { projectUserRepository.findByProjectId(102L) } returns listOf(pu)

            service.isOnlyManager(42L) shouldBe true
        }

        it("MANAGER인 프로젝트에 다른 매니저가 더 있으면 false를 반환해야 한다") {
            val managerRole = Role(id = RoleType.MANAGER.roleType, name = "manager")
            val memberRole = Role(id = RoleType.MEMBER.roleType, name = "member")
            // role.id가 null인 데이터도 매니저 집계 필터에서 안전하게 제외되어야 한다(role.id nullable 분기 커버)
            val nullIdRole = Role(id = null, name = "broken-role")
            val user = User(id = 43L, loginId = "co-manager", name = "공동매니저")
            val otherManager = User(id = 44L, loginId = "other-manager", name = "다른매니저")
            val member = User(id = 45L, loginId = "just-member", name = "그냥멤버")
            val brokenRoleUser = User(id = 48L, loginId = "broken-role-user", name = "깨진역할사용자")
            val project = Project(id = 103L, name = "proj103")
            val pu = ProjectUser(id = 4L, user = user, project = project, role = managerRole)
            val puOther = ProjectUser(id = 5L, user = otherManager, project = project, role = managerRole)
            val puMember = ProjectUser(id = 6L, user = member, project = project, role = memberRole)
            val puBroken = ProjectUser(id = 13L, user = brokenRoleUser, project = project, role = nullIdRole)
            every { projectUserRepository.findByUserId(43L) } returns listOf(pu)
            every { projectUserRepository.findByProjectId(103L) } returns listOf(pu, puOther, puMember, puBroken)

            service.isOnlyManager(43L) shouldBe false
        }

        it("여러 프로젝트 중 첫 프로젝트는 공동매니저이고 두번째 프로젝트가 유일 매니저면 true를 반환해야 한다 (루프 진행 확인)") {
            val managerRole = Role(id = RoleType.MANAGER.roleType, name = "manager")
            val user = User(id = 46L, loginId = "multi-project-manager", name = "다중매니저")
            val otherManager = User(id = 47L, loginId = "other-manager2", name = "다른매니저2")
            val projectA = Project(id = 104L, name = "projA")
            val projectB = Project(id = 105L, name = "projB")
            val puA = ProjectUser(id = 7L, user = user, project = projectA, role = managerRole)
            val puAOther = ProjectUser(id = 8L, user = otherManager, project = projectA, role = managerRole)
            val puB = ProjectUser(id = 9L, user = user, project = projectB, role = managerRole)
            every { projectUserRepository.findByUserId(46L) } returns listOf(puA, puB)
            every { projectUserRepository.findByProjectId(104L) } returns listOf(puA, puAOther)
            every { projectUserRepository.findByProjectId(105L) } returns listOf(puB)

            service.isOnlyManager(46L) shouldBe true
        }
    }

    describe("SiteService.getMailList") {
        it("all=true이면 이메일이 비어있지 않은 모든 사용자의 이메일을 정렬해 반환해야 한다") {
            val u1 = User(id = 50L, loginId = "u1", name = "u1", email = "b@example.com")
            val u2 = User(id = 51L, loginId = "u2", name = "u2", email = "") // 빈 이메일 -> 제외
            val u3 = User(id = 52L, loginId = "u3", name = "u3", email = "a@example.com")
            every { userRepository.findAll() } returns listOf(u1, u2, u3)

            val result = service.getMailList(true, emptyList())

            result shouldContainExactly listOf("a@example.com", "b@example.com")
        }

        it("all=true이고 사용자가 한 명도 없으면 빈 리스트를 반환해야 한다") {
            every { userRepository.findAll() } returns emptyList()

            val result = service.getMailList(true, emptyList())

            result shouldBe emptyList()
        }

        it("all=false이고 projectNames가 비어있으면 빈 리스트를 반환해야 한다") {
            val result = service.getMailList(false, emptyList())

            result shouldBe emptyList()
        }

        it("all=false이고 owner/name 형식이 아닌 프로젝트명은 무시해야 한다") {
            val result = service.getMailList(false, listOf("no-slash-name", "a/b/c"))

            result shouldBe emptyList()
        }

        it("all=false이고 프로젝트를 찾을 수 없으면 해당 항목은 무시해야 한다") {
            every { projectRepository.findByOwnerAndName("owner1", "missing") } returns Optional.empty()

            val result = service.getMailList(false, listOf("owner1/missing"))

            result shouldBe emptyList()
        }

        it("all=false이면 유효한 프로젝트의 참여자 이메일만 모아 정렬해 반환해야 한다") {
            val projectEmpty = Project(id = 200L, name = "empty-proj")
            every { projectRepository.findByOwnerAndName("owner2", "empty-proj") } returns Optional.of(projectEmpty)
            every { projectUserRepository.findByProjectId(200L) } returns emptyList()

            val role = Role(id = RoleType.MEMBER.roleType, name = "member")
            val project = Project(id = 201L, name = "real-proj")
            val userWithEmail = User(id = 60L, loginId = "hasEmail", name = "메일있음", email = "z@example.com")
            val userBlankEmail = User(id = 61L, loginId = "noEmail", name = "메일없음", email = "")
            val pu1 = ProjectUser(id = 10L, user = userWithEmail, project = project, role = role)
            val pu2 = ProjectUser(id = 11L, user = userBlankEmail, project = project, role = role)
            every { projectRepository.findByOwnerAndName("owner3", "real-proj") } returns Optional.of(project)
            every { projectUserRepository.findByProjectId(201L) } returns listOf(pu1, pu2)

            val result = service.getMailList(false, listOf("owner2/empty-proj", "owner3/real-proj"))

            result shouldContainExactly listOf("z@example.com")
        }
    }

    describe("SiteService.getNoAvatarUsers (P2-03)") {
        it("아바타 첨부파일(USER_AVATAR 컨테이너)이 없는 활성 사용자만 반환해야 한다") {
            val withAvatar = User(id = 1L, loginId = "hasAvatar", name = "아바타있음", state = UserState.ACTIVE)
            val withoutAvatar = User(id = 2L, loginId = "noAvatar", name = "아바타없음", state = UserState.ACTIVE)
            every { userRepository.findAll() } returns listOf(withAvatar, withoutAvatar)
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, "1") } returns
                listOf(Attachment(id = 900L, containerType = ResourceType.USER_AVATAR, containerId = "1"))
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, "2") } returns emptyList()

            val result = service.getNoAvatarUsers()

            result.size shouldBe 1
            result.first()["loginId"] shouldBe "noAvatar"
        }

        it("SITE_ADMIN 상태 사용자도 대상에 포함되고, ACTIVE/SITE_ADMIN이 아닌 사용자는 제외되어야 한다") {
            val admin = User(id = 3L, loginId = "adminNoAvatar", name = "관리자", state = UserState.SITE_ADMIN, email = "")
            val locked = User(id = 4L, loginId = "lockedUser", name = "잠긴사용자", state = UserState.LOCKED)
            every { userRepository.findAll() } returns listOf(admin, locked)
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, "3") } returns emptyList()

            val result = service.getNoAvatarUsers()

            result.size shouldBe 1
            result.first()["loginId"] shouldBe "adminNoAvatar"
            result.first()["email"] shouldBe ""
            // LOCKED 상태는 활성 사용자 필터에서 걸러지므로 attachmentRepository가 호출되지 않아야 한다
            verify(exactly = 0) { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, "4") }
        }
    }

    describe("SiteService.setUserAvatar (P2-03, yona SiteApp.setAttachmentToUserAvatar 대응)") {
        it("이미지 첨부파일을 대상 사용자의 아바타(USER_AVATAR 컨테이너)로 지정하고 기존 아바타는 삭제해야 한다") {
            val attachment = Attachment(id = 100L, name = "photo.png", mimeType = "image/png")
            val oldAvatar = Attachment(id = 50L, containerType = ResourceType.USER_AVATAR, containerId = "10")
            every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(targetUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, "10") } returns listOf(oldAvatar)
            every { attachmentRepository.deleteAll(listOf(oldAvatar)) } returns Unit
            every { attachmentRepository.save(attachment) } returns attachment

            service.setUserAvatar(100L, "gildong@example.com")

            attachment.containerType shouldBe ResourceType.USER_AVATAR
            attachment.containerId shouldBe "10"
            verify(exactly = 1) { attachmentRepository.deleteAll(listOf(oldAvatar)) }
            verify(exactly = 1) { attachmentRepository.save(attachment) }
        }

        it("이미지가 아닌 첨부파일이면 예외가 발생해야 한다") {
            val attachment = Attachment(id = 101L, name = "doc.pdf", mimeType = "application/pdf")
            every { attachmentRepository.findById(101L) } returns Optional.of(attachment)
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(targetUser)

            shouldThrow<IllegalArgumentException> {
                service.setUserAvatar(101L, "gildong@example.com")
            }

            verify(exactly = 0) { attachmentRepository.save(any()) }
        }

        it("대상 사용자를 찾을 수 없으면 예외가 발생해야 한다") {
            val attachment = Attachment(id = 102L, name = "photo.png", mimeType = "image/png")
            every { attachmentRepository.findById(102L) } returns Optional.of(attachment)
            every { userRepository.findByEmail("unknown@example.com") } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.setUserAvatar(102L, "unknown@example.com")
            }
        }

        it("첨부파일 자체를 찾을 수 없으면 예외가 발생해야 한다") {
            every { attachmentRepository.findById(999L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.setUserAvatar(999L, "gildong@example.com")
            }

            verify(exactly = 0) { userRepository.findByEmail(any()) }
        }

        it("mimeType이 null이면 이미지가 아닌 것으로 간주해 예외가 발생해야 한다") {
            val attachment = Attachment(id = 103L, name = "unknown-type", mimeType = null)
            every { attachmentRepository.findById(103L) } returns Optional.of(attachment)

            shouldThrow<IllegalArgumentException> {
                service.setUserAvatar(103L, "gildong@example.com")
            }

            verify(exactly = 0) { userRepository.findByEmail(any()) }
        }
    }
})
