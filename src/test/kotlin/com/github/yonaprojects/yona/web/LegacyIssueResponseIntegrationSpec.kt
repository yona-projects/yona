package com.github.yonaprojects.yona.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueEventRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategoryRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.http.MediaType
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Transactional
class LegacyIssueResponseIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val issueEventRepository: IssueEventRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val milestoneRepository: MilestoneRepository,
    private val attachmentRepository: AttachmentRepository,
    private val entityManager: EntityManager,
    private val messageSource: MessageSource
) : AbstractIntegrationTest() {

    init {
        describe("legacy issue response contract") {
            it("GET, PUT and state PATCH return mapped results and persisted events without user secrets") {
                val mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                    .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                    .build()
                val mapper = ObjectMapper()
                val owner = userRepository.save(User(
                    loginId = "legacy-issue-contract", name = "Alice [Engineering]", englishName = "Alice",
                    email = "legacy-issue-contract@example.com", password = "private-password", passwordSalt = "private-salt"
                ))
                val project = projectRepository.save(Project(owner = owner.loginId, name = "response-contract"))
                val role = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                }
                projectUserRepository.save(ProjectUser(user = owner, project = project, role = role))
                val category = issueLabelCategoryRepository.save(IssueLabelCategory(name = "kind", project = project))
                val milestone = milestoneRepository.save(Milestone(title = "Release", project = project))
                val created = Instant.parse("2026-01-02T03:04:05Z")
                val issue = issueRepository.save(Issue(
                    title = "Original title", body = "Original body", number = 1, project = project,
                    authorId = owner.id, authorLoginId = owner.loginId, authorName = owner.name,
                    createdDate = created, updatedDate = created, milestone = milestone,
                    assignee = Assignee(user = owner, project = project),
                    labels = mutableSetOf(IssueLabel(name = "bug", color = "#ff0000", category = category, project = project))
                ))
                val comment = issueCommentRepository.save(IssueComment(
                    issue = issue, contents = "Comment", authorId = owner.id, createdDate = created
                ))
                issueCommentRepository.save(IssueComment(
                    issue = issue, parentComment = comment, contents = "Reply", authorId = owner.id, createdDate = created.plusSeconds(1)
                ))
                attachmentRepository.save(Attachment(
                    name = "issue.txt", hash = "issue-hash", containerType = ResourceType.ISSUE_POST,
                    containerId = issue.id.toString(), ownerLoginId = owner.loginId
                ))
                attachmentRepository.save(Attachment(
                    name = "comment.txt", hash = "comment-hash", containerType = ResourceType.ISSUE_COMMENT,
                    containerId = comment.id.toString(), ownerLoginId = owner.loginId
                ))
                entityManager.flush()
                entityManager.clear()

                val details = YonaUserDetails(
                    id = owner.id!!, loginId = owner.loginId, passwordVal = "hashed", passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )
                val path = "/-_-api/v1/owners/${owner.loginId}/projects/${project.name}/issues/1"
                fun response(request: MockHttpServletRequestBuilder) = mockMvc.perform(
                    request.with(user(details)).with(csrf()).locale(Locale.ENGLISH)
                ).andReturn().response.let { response ->
                    response.status shouldBe 200
                    val body = response.contentAsString
                    body shouldNotContain "password"
                    body shouldNotContain "private-salt"
                    body shouldNotContain "projectUsers"
                    mapper.readTree(body)
                }

                val initial = response(get(path))
                initial.fieldNames().asSequence().toSet() shouldBe setOf("result")
                val result = initial.path("result")
                result.path("title").asText() shouldBe "Original title"
                result.path("author").path("loginId").asText() shouldBe owner.loginId
                result.path("author").path("email").asText() shouldBe owner.email
                result.path("assignees").single().path("loginId").asText() shouldBe owner.loginId
                result.path("labels").single().path("labelName").asText() shouldBe "bug"
                result.path("labels").single().path("labelColor").asText() shouldBe "#ff0000"
                result.path("labels").single().path("category").asText() shouldBe "kind"
                result.path("milestoneId").asLong() shouldBe milestone.id
                result.path("milestoneTitle").asText() shouldBe "Release"
                result.path("attachments").single().path("name").asText() shouldBe "issue.txt"
                result.path("comments").single().path("body").asText() shouldBe "Comment"
                result.path("comments").single().path("author").path("loginId").asText() shouldBe owner.loginId
                result.path("comments").single().path("attachments").single().path("name").asText() shouldBe "comment.txt"
                result.path("comments").single().path("childComments").single().path("body").asText() shouldBe "Reply"
                result.has("events") shouldBe false

                val closed = response(patch(path).contentType(MediaType.APPLICATION_JSON).content("""{"state":"closed"}"""))
                closed.path("result").path("state").asText() shouldBe "CLOSED"
                val stateEvent = closed.path("result").path("events").single()
                val persisted = issueEventRepository.findByIssueOrderByCreatedAsc(issue).single()
                stateEvent.fieldNames().asSequence().toSet() shouldBe setOf(
                    "id", "createdDate", "eventType", "eventDescription", "oldValue", "newValue", "actor"
                )
                stateEvent.path("id").asLong() shouldBe persisted.id
                OffsetDateTime.parse(stateEvent.path("createdDate").asText(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"))
                    .toInstant() shouldBe persisted.created.truncatedTo(ChronoUnit.SECONDS)
                stateEvent.path("eventType").asText() shouldBe "ISSUE_STATE_CHANGED"
                stateEvent.path("eventDescription").asText() shouldBe messageSource.getMessage(
                    EventType.ISSUE_STATE_CHANGED.messageKey, null, Locale.ENGLISH
                )
                stateEvent.path("oldValue").asText() shouldBe "OPEN"
                stateEvent.path("newValue").asText() shouldBe "CLOSED"
                stateEvent.path("actor").fieldNames().asSequence().toSet() shouldBe setOf("name", "loginId", "englishName")
                stateEvent.path("actor").path("name").asText() shouldBe "Alice"
                stateEvent.path("actor").path("loginId").asText() shouldBe owner.loginId
                stateEvent.path("actor").path("englishName").asText() shouldBe "Alice"

                val updated = response(put(path).contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Updated title","body":"Updated body"}"""))
                updated.path("result").path("title").asText() shouldBe "Updated title"
                updated.path("result").path("body").asText() shouldBe "Updated body"
                val bodyEvent = updated.path("result").path("events").single { it.path("eventType").asText() == "ISSUE_BODY_CHANGED" }
                bodyEvent.path("oldValue").asText() shouldBe "Original body"
                bodyEvent.path("newValue").asText() shouldBe "Updated body"
                entityManager.flush()
                entityManager.clear()
                response(get(path)) shouldBe updated

                val modern = response(get("/api/v1/projects/${owner.loginId}/${project.name}/issues/1"))
                modern.path("title").asText() shouldBe "Updated title"
                modern.has("result") shouldBe false
            }
        }
    }
}
