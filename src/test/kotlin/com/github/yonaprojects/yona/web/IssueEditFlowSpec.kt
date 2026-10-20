package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueEventRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import jakarta.persistence.EntityManager

// v1.6 AbstractPostingApp.editPosting: 폼 바인딩 객체와 DB 원본(original)을 분리해 original.body와 비교한 뒤 history·알림·TitleHead를
// 갱신하고, 끝에서 attachUploadFilesToPost(original)로 임시 첨부를 컨테이너로 옮겼다. next 폼 컨트롤러는 서비스 호출 전에 영속 엔티티의
// title/body를 먼저 덮어써서 서비스가 "이전 값"을 새 값으로 읽고, 첨부도 옮기지 않았다.
@Transactional
class IssueEditFlowSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository,
    private val issueEventRepository: IssueEventRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val attachmentService: AttachmentService,
    private val attachmentRepository: AttachmentRepository,
    private val entityManager: EntityManager
) : AbstractIntegrationTest() {

    private val mockMvc: MockMvc by lazy {
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
    }

    private fun memberRole() = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
        roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
    }

    private fun details(u: User) = YonaUserDetails(
        id = u.id ?: 0L, loginId = u.loginId, passwordVal = "h", passwordSalt = "s",
        authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
    )

    init {
        describe("이슈 수정 폼 POST /{owner}/{project}/issue/{n}/edit") {
            lateinit var author: User
            lateinit var project: Project
            lateinit var issue: Issue

            beforeEach {
                author = userRepository.save(User(loginId = "edit-author", name = "작성자", email = "edit-author@yona.io"))
                project = projectRepository.save(Project(name = "edit-proj", owner = "edit-owner", projectScope = ProjectScope.PUBLIC))
                projectUserRepository.save(ProjectUser(project = project, user = author, role = memberRole()))
                issue = issueRepository.save(
                    Issue(
                        title = "old title", body = "old body", project = project, number = 1L,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.OPEN
                    )
                )
                entityManager.flush()
                entityManager.clear()
            }

            it("본문을 바꾸면 이전 본문이 history에 남고 ISSUE_BODY_CHANGED 이벤트가 기록된다") {
                mockMvc.perform(
                    post("/${project.owner}/${project.name}/issue/1/edit")
                        .with(user(details(author))).with(csrf())
                        .param("title", "old title").param("body", "new body")
                ).andExpect(status().is3xxRedirection)
                entityManager.flush()
                entityManager.clear()

                val reloaded = issueRepository.findById(issue.id!!).orElseThrow()
                reloaded.body shouldBe "new body"
                // 이력은 단어 단위 diff HTML(history-made-by 머리줄 + diff-deleted/diff-added span)로 저장된다.
                val history = reloaded.history ?: ""
                history shouldContain "edit-author"
                history shouldContain "diff-deleted"
                issueEventRepository.findByIssueOrderByCreatedAsc(reloaded)
                    .filter { it.eventType == EventType.ISSUE_BODY_CHANGED } shouldHaveSize 1
            }

            it("수정 폼에서 새로 올린 임시 첨부가 이슈 컨테이너로 옮겨진다") {
                val (attach, _) = attachmentService.store(
                    "png-bytes".byteInputStream(), "a.png", ResourceType.NOT_A_RESOURCE, "", author.loginId!!
                )
                entityManager.flush()

                mockMvc.perform(
                    post("/${project.owner}/${project.name}/issue/1/edit")
                        .with(user(details(author))).with(csrf())
                        .param("title", "old title").param("body", "old body ![a](/files/${attach.id})")
                        .param("temporaryUploadFiles", attach.id.toString())
                ).andExpect(status().is3xxRedirection)
                entityManager.flush()
                entityManager.clear()

                val moved = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, issue.id.toString())
                moved shouldHaveSize 1
                moved[0].id shouldBe attach.id
            }
        }
    }
}
