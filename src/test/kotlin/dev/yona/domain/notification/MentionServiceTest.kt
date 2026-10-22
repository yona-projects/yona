package dev.yona.domain.notification

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals

/**
 * 본문의 `@멘션`을 읽어 멘션 기록(`mention`)을 갱신하는 규칙을 검증한다.
 *
 * 원본: `app/models/NotificationEvent.java:1518-1580`(`getMentionedUsers`), `app/models/Mention.java`(`update`).
 */
@Transactional
class MentionServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: MentionService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var mentions: MentionRepository

    private var sequence = 0

    private fun user(login: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = login, name = login, email = "$login@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun mentioned(body: String): Set<String?> = service.mentionedUsers(body).map { it.loginId }.toSet()

    @Test
    fun `본문의 아이디 멘션을 읽는다`() {
        user("alice"); user("bob")
        assertEquals(setOf("alice", "bob"), mentioned("안녕 @alice 그리고 @bob."))
        assertEquals(emptySet(), mentioned("@nobody 만 있다"))
        assertEquals(emptySet(), mentioned("멘션이 없다"))
    }

    @Test
    fun `조직 이름을 멘션하면 조직 구성원 모두이다`() {
        val org = organizations.save(Organization(name = "team${++sequence}", created = Date()))
        val a = user("orga$sequence"); val b = user("orgb$sequence")
        listOf(a, b).forEach { organizationUsers.save(OrganizationUser(user = it, organization = org, role = roles.findById(RoleType.ORG_MEMBER.id).get())) }
        assertEquals(setOf(a.loginId, b.loginId), mentioned("@${org.name} 모두 확인해 주세요"))
    }

    @Test
    fun `소유자 슬래시 프로젝트를 멘션하면 프로젝트 구성원 모두이다`() {
        val a = user("pa${++sequence}"); val b = user("pb$sequence")
        val project = projects.save(Project(owner = "mo$sequence", name = "mp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        listOf(a, b).forEach { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        assertEquals(setOf(a.loginId, b.loginId), mentioned("@${project.owner}/${project.name} 확인"))
        assertEquals(emptySet(), mentioned("@nobody/none"))
    }

    @Test
    fun `멘션 기록은 새로 생기고 사라진 사람만 바뀐다`() {
        val a = user("ma"); val b = user("mb"); val c = user("mc")
        service.update(ResourceType.ISSUE_POST, "10", "@ma @mb")
        assertEquals(setOf("ma", "mb"), mentions.findAll().filter { it.resourceId == "10" }.map { it.user?.loginId }.toSet())
        val firstIds = mentions.findAll().filter { it.resourceId == "10" && it.user?.loginId == "mb" }.map { it.id }
        service.update(ResourceType.ISSUE_POST, "10", "@mb @mc")
        val after = mentions.findAll().filter { it.resourceId == "10" }
        assertEquals(setOf("mb", "mc"), after.map { it.user?.loginId }.toSet())
        assertEquals(firstIds, after.filter { it.user?.loginId == "mb" }.map { it.id })
        service.update(ResourceType.ISSUE_POST, "10", "멘션 없음")
        assertEquals(0, mentions.findAll().count { it.resourceId == "10" })
        assertEquals(setOf(a.loginId, b.loginId, c.loginId).size, 3)
    }

    @Test
    fun `본문이 없으면 기록을 건드리지 않는다`() {
        user("ka")
        service.update(ResourceType.ISSUE_POST, "20", "@ka")
        service.update(ResourceType.ISSUE_POST, "20", null)
        assertEquals(1, mentions.findAll().count { it.resourceId == "20" })
    }

    @Test
    fun `다른 종류나 번호의 기록은 건드리지 않는다`() {
        user("la")
        service.update(ResourceType.ISSUE_POST, "30", "@la")
        service.update(ResourceType.BOARD_POST, "30", "")
        service.update(ResourceType.ISSUE_POST, "31", "")
        assertEquals(1, mentions.findAll().count { it.resourceId == "30" && it.resourceType == ResourceType.ISSUE_POST })
    }
}
