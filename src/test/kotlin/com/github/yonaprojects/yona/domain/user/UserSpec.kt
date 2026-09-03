package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.Locale

// yona models/User.java:563-569 isSiteManager() 대응 (P1-119). yona는 별도 SiteAdmin 테이블
// 소속 여부로만 판단하고 loginId 자체를 특별 취급하지 않는다 — loginId=="admin"이면 상태와
// 무관하게 항상 site manager로 취급하는 분기는 yona에 없는 yona 자체 버그였다.
class UserSpec : DescribeSpec({
    describe("User.isSiteManager") {
        it("state가 SITE_ADMIN이면 true여야 한다") {
            val user = User(loginId = "someone", name = "누군가", email = "someone@example.com", state = UserState.SITE_ADMIN)
            user.isSiteManager shouldBe true
        }

        it("loginId가 admin이어도 state가 SITE_ADMIN이 아니면 false여야 한다") {
            val user = User(loginId = "admin", name = "관리자아님", email = "admin@example.com", state = UserState.ACTIVE)
            user.isSiteManager shouldBe false
        }

        it("state가 SITE_ADMIN이 아니고 loginId도 admin이 아니면 false여야 한다") {
            val user = User(loginId = "gildong", name = "홍길동", email = "gildong@example.com", state = UserState.ACTIVE)
            user.isSiteManager shouldBe false
        }
    }

    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val user = User()
            val project = Project(id = 1L)
            val organization = Organization(id = 1L)
            val projectUser = ProjectUser(user = user, project = project, role = Role(id = RoleType.MEMBER.roleType))
            val organizationUser = OrganizationUser(user = user, organization = organization, role = Role(id = RoleType.ORG_MEMBER.roleType))
            val email = Email(user = user, email = "acc@example.com")
            val lastStateModified = Instant.now()
            val created = Instant.now()

            user.id = 1L
            user.name = "이름"
            user.englishName = "English Name"
            user.loginId = "loginid"
            user.password = "pw"
            user.passwordSalt = "salt"
            user.email = "user@example.com"
            user.token = "token"
            user.rememberMe = true
            user.state = UserState.LOCKED
            user.lastStateModifiedDate = lastStateModified
            user.createdDate = created
            user.lang = "ko"
            user.isGuest = true
            user.projectUsers = mutableListOf(projectUser)
            user.organizationUsers = mutableListOf(organizationUser)
            user.enrolledProjects = mutableListOf(project)
            user.enrolledOrganizations = mutableListOf(organization)
            user.emails = mutableListOf(email)
            user.avatarId = 42L

            user.id shouldBe 1L
            user.name shouldBe "이름"
            user.englishName shouldBe "English Name"
            user.loginId shouldBe "loginid"
            user.password shouldBe "pw"
            user.passwordSalt shouldBe "salt"
            user.email shouldBe "user@example.com"
            user.token shouldBe "token"
            user.rememberMe shouldBe true
            user.state shouldBe UserState.LOCKED
            user.lastStateModifiedDate shouldBe lastStateModified
            user.createdDate shouldBe created
            user.lang shouldBe "ko"
            user.isGuest shouldBe true
            user.projectUsers shouldBe mutableListOf(projectUser)
            user.organizationUsers shouldBe mutableListOf(organizationUser)
            user.enrolledProjects shouldBe mutableListOf(project)
            user.enrolledOrganizations shouldBe mutableListOf(organization)
            user.emails shouldBe mutableListOf(email)
            user.avatarId shouldBe 42L
        }
    }

    describe("getPreferredLanguage()") {
        it("lang이 설정되어 있으면 그 값을 반환해야 한다") {
            val user = User(lang = "ko")
            user.getPreferredLanguage() shouldBe "ko"
        }

        it("lang이 null이면 시스템 기본 로케일의 언어를 반환해야 한다") {
            val user = User(lang = null)
            user.getPreferredLanguage() shouldBe Locale.getDefault().language
        }
    }

    describe("enroll(Project)/cancelEnroll(Project)") {
        it("아직 등록되지 않은 project는 추가되어야 한다") {
            val user = User()
            val project = Project(id = 1L)
            user.enroll(project)
            user.enrolledProjects shouldBe mutableListOf(project)
        }

        it("이미 등록된 project는 중복 추가되지 않아야 한다") {
            val user = User()
            val project = Project(id = 1L)
            user.enrolledProjects = mutableListOf(project)
            user.enroll(project)
            user.enrolledProjects.size shouldBe 1
        }

        it("등록된 project는 취소 시 제거되어야 한다") {
            val user = User()
            val project = Project(id = 1L)
            user.enrolledProjects = mutableListOf(project)
            user.cancelEnroll(project)
            user.enrolledProjects shouldBe mutableListOf()
        }

        it("등록되지 않은 project를 취소해도 예외 없이 무시되어야 한다") {
            val user = User()
            val project = Project(id = 1L)
            user.cancelEnroll(project)
            user.enrolledProjects shouldBe mutableListOf()
        }
    }

    describe("enroll(Organization)/cancelEnroll(Organization)") {
        it("아직 등록되지 않은 organization은 추가되어야 한다") {
            val user = User()
            val organization = Organization(id = 1L)
            user.enroll(organization)
            user.enrolledOrganizations shouldBe mutableListOf(organization)
        }

        it("이미 등록된 organization은 중복 추가되지 않아야 한다") {
            val user = User()
            val organization = Organization(id = 1L)
            user.enrolledOrganizations = mutableListOf(organization)
            user.enroll(organization)
            user.enrolledOrganizations.size shouldBe 1
        }

        it("등록된 organization은 취소 시 제거되어야 한다") {
            val user = User()
            val organization = Organization(id = 1L)
            user.enrolledOrganizations = mutableListOf(organization)
            user.cancelEnroll(organization)
            user.enrolledOrganizations shouldBe mutableListOf()
        }

        it("등록되지 않은 organization을 취소해도 예외 없이 무시되어야 한다") {
            val user = User()
            val organization = Organization(id = 1L)
            user.cancelEnroll(organization)
            user.enrolledOrganizations shouldBe mutableListOf()
        }
    }

    describe("addEmail()/removeEmail()") {
        it("등록되지 않은 email은 추가되고 email.user가 설정되어야 한다") {
            val user = User()
            val other = User()
            val email = Email(user = other, email = "a@example.com")
            user.addEmail(email)
            user.emails shouldBe mutableListOf(email)
            email.user shouldBe user
        }

        it("이미 등록된 email은 중복 추가되지 않아야 한다") {
            val user = User()
            val email = Email(user = user, email = "a@example.com")
            user.emails = mutableListOf(email)
            user.addEmail(email)
            user.emails.size shouldBe 1
        }

        it("등록된 email은 제거되어야 한다") {
            val user = User()
            val email = Email(user = user, email = "a@example.com")
            user.emails = mutableListOf(email)
            user.removeEmail(email)
            user.emails shouldBe mutableListOf()
        }

        it("등록되지 않은 email을 제거해도 예외 없이 무시되어야 한다") {
            val user = User()
            val email = Email(user = user, email = "a@example.com")
            user.removeEmail(email)
            user.emails shouldBe mutableListOf()
        }
    }

    describe("has()") {
        it("emails가 비어있으면 false여야 한다") {
            val user = User()
            user.has("a@example.com") shouldBe false
        }

        it("일치하는 email이 있으면 true여야 한다") {
            val user = User()
            user.emails = mutableListOf(Email(user = user, email = "a@example.com"))
            user.has("a@example.com") shouldBe true
        }

        it("일치하는 email이 없으면 false여야 한다") {
            val user = User()
            user.emails = mutableListOf(Email(user = user, email = "a@example.com"))
            user.has("b@example.com") shouldBe false
        }
    }

    describe("getDisplayName()") {
        it("name을 그대로 반환해야 한다") {
            val user = User(name = "홍길동")
            user.getDisplayName() shouldBe "홍길동"
        }
    }

    describe("getDisplayName(forCurrentUser)") {
        it("englishName이 null이면 name을 반환해야 한다") {
            val user = User(name = "홍길동", englishName = null, lang = "ko")
            val forCurrentUser = User(lang = "en")
            user.getDisplayName(forCurrentUser) shouldBe "홍길동"
        }

        it("englishName이 blank이면 name을 반환해야 한다") {
            val user = User(name = "홍길동", englishName = "   ", lang = "ko")
            val forCurrentUser = User(lang = "en")
            user.getDisplayName(forCurrentUser) shouldBe "홍길동"
        }

        it("lang이 null이면 name을 반환해야 한다") {
            val user = User(name = "홍길동", englishName = "Gildong Hong", lang = null)
            val forCurrentUser = User(lang = "en")
            user.getDisplayName(forCurrentUser) shouldBe "홍길동"
        }

        it("forCurrentUser의 lang이 null이면 name을 반환해야 한다") {
            val user = User(name = "홍길동", englishName = "Gildong Hong", lang = "ko")
            val forCurrentUser = User(lang = null)
            user.getDisplayName(forCurrentUser) shouldBe "홍길동"
        }

        it("forCurrentUser의 lang이 en으로 시작하지 않으면 name을 반환해야 한다") {
            val user = User(name = "홍길동", englishName = "Gildong Hong", lang = "ko")
            val forCurrentUser = User(lang = "ko")
            user.getDisplayName(forCurrentUser) shouldBe "홍길동"
        }

        it("모든 조건을 만족하면 englishName과 부서 부분을 반환해야 한다") {
            val user = User(name = "홍길동[개발팀]", englishName = "Gildong Hong", lang = "ko")
            val forCurrentUser = User(lang = "en_US")
            user.getDisplayName(forCurrentUser) shouldBe "Gildong Hong [개발팀]"
        }
    }

    describe("getPureNameOnly()") {
        it("괄호가 없으면 name을 그대로 반환해야 한다") {
            User(name = "홍길동").getPureNameOnly() shouldBe "홍길동"
        }

        it("대괄호가 있으면 그 앞부분만 반환해야 한다") {
            User(name = "홍길동[개발팀]").getPureNameOnly() shouldBe "홍길동"
        }

        it("소괄호가 있으면 그 앞부분만 반환해야 한다") {
            User(name = "홍길동(개발팀)").getPureNameOnly() shouldBe "홍길동"
        }

        it("대괄호와 소괄호가 모두 있으면 더 앞의 것을 기준으로 잘라야 한다") {
            User(name = "홍길동 (개발팀) [부가정보]").getPureNameOnly() shouldBe "홍길동"
        }
    }

    describe("getPureNameOnly(targetLang)") {
        it("englishName이 null이면 괄호 제거 로직으로 fallback해야 한다") {
            val user = User(name = "홍길동", englishName = null, lang = "ko")
            user.getPureNameOnly("en") shouldBe "홍길동"
        }

        it("englishName이 blank이면 괄호 제거 로직으로 fallback해야 한다") {
            val user = User(name = "홍길동", englishName = "   ", lang = "ko")
            user.getPureNameOnly("en") shouldBe "홍길동"
        }

        it("lang이 null이면 괄호 제거 로직으로 fallback해야 한다") {
            val user = User(name = "홍길동", englishName = "Gildong Hong", lang = null)
            user.getPureNameOnly("en") shouldBe "홍길동"
        }

        it("targetLang이 null이면 괄호 제거 로직으로 fallback해야 한다") {
            val user = User(name = "홍길동", englishName = "Gildong Hong", lang = "ko")
            user.getPureNameOnly(null) shouldBe "홍길동"
        }

        it("targetLang이 en으로 시작하지 않으면 괄호 제거 로직으로 fallback해야 한다 (대괄호 포함 name)") {
            val user = User(name = "홍길동[개발팀]", englishName = "Gildong Hong", lang = "ko")
            user.getPureNameOnly("ko") shouldBe "홍길동"
        }

        it("모든 조건을 만족하면 englishName을 그대로 반환해야 한다") {
            val user = User(name = "홍길동[개발팀]", englishName = "Gildong Hong", lang = "ko")
            user.getPureNameOnly("en") shouldBe "Gildong Hong"
        }
    }

    describe("extractDepartmentPart()") {
        it("괄호가 없으면 name 전체를 반환해야 한다") {
            User(name = "홍길동").extractDepartmentPart() shouldBe "홍길동"
        }

        it("대괄호가 있으면 첫 반복에서 break하여 대괄호 이후를 반환해야 한다") {
            User(name = "홍길동[개발팀](추가)").extractDepartmentPart() shouldBe "[개발팀](추가)"
        }

        it("소괄호만 있으면 두 번째 반복에서 break하여 소괄호 이후를 반환해야 한다") {
            User(name = "홍길동(개발팀)").extractDepartmentPart() shouldBe "(개발팀)"
        }
    }

    describe("avatarUrl") {
        it("avatarId가 null이면 기본 이미지 경로를 반환해야 한다") {
            val user = User()
            user.avatarId = null
            user.avatarUrl(64) shouldBe "/assets/images/default-avatar-128.png"
            user.avatarUrl shouldBe "/assets/images/default-avatar-128.png"
        }

        it("avatarId가 설정되어 있으면 파일 경로를 반환해야 한다") {
            val user = User()
            user.avatarId = 7L
            user.avatarUrl(64) shouldBe "/files/7"
            user.avatarUrl shouldBe "/files/7"
        }
    }

    describe("isMemberOf()") {
        it("projectUsers가 비어있으면 false여야 한다") {
            val user = User()
            val project = Project(id = 1L)
            user.isMemberOf(project) shouldBe false
        }

        it("해당 project에 소속되어 있으면 true여야 한다") {
            val user = User()
            val project = Project(id = 1L)
            user.projectUsers = mutableListOf(ProjectUser(user = user, project = project, role = Role(id = RoleType.MEMBER.roleType)))
            user.isMemberOf(project) shouldBe true
        }

        it("다른 project에만 소속되어 있으면 false여야 한다") {
            val user = User()
            val project = Project(id = 1L)
            val otherProject = Project(id = 2L)
            user.projectUsers = mutableListOf(ProjectUser(user = user, project = otherProject, role = Role(id = RoleType.MEMBER.roleType)))
            user.isMemberOf(project) shouldBe false
        }
    }

    describe("isManagerOf()") {
        it("해당 project에 MANAGER 역할로 소속되어 있으면 true여야 한다") {
            val user = User()
            val project = Project(id = 1L)
            user.projectUsers = mutableListOf(ProjectUser(user = user, project = project, role = Role(id = RoleType.MANAGER.roleType)))
            user.isManagerOf(project) shouldBe true
        }

        it("해당 project에 소속되어 있어도 MANAGER 역할이 아니면 false여야 한다") {
            val user = User()
            val project = Project(id = 1L)
            user.projectUsers = mutableListOf(ProjectUser(user = user, project = project, role = Role(id = RoleType.MEMBER.roleType)))
            user.isManagerOf(project) shouldBe false
        }

        it("다른 project에 MANAGER 역할로 소속되어 있으면 false여야 한다") {
            val user = User()
            val project = Project(id = 1L)
            val otherProject = Project(id = 2L)
            user.projectUsers = mutableListOf(ProjectUser(user = user, project = otherProject, role = Role(id = RoleType.MANAGER.roleType)))
            user.isManagerOf(project) shouldBe false
        }
    }
})
