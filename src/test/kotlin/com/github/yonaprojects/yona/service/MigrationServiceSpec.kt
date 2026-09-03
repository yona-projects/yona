package com.github.yonaprojects.yona.service

import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Optional

/**
 * MigrationService는 legacy yona 프로젝트(이슈/게시글/라벨/마일스톤)를 외부 시스템(GitHub 등)으로
 * 내보내기 위한 순수 조회/변환 서비스다. 리포지토리 의존성은 모두 mockk로 격리하고,
 * getOAuthToken 내부에서 직접 생성하는 RestTemplate은 mockkConstructor로 모킹해 실제 네트워크
 * 호출 없이 분기를 검증한다.
 */
class MigrationServiceSpec : DescribeSpec({

    // MigrationService의 private formatter와 동일한 패턴으로 기대값을 계산한다.
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.systemDefault())

    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val issueLabelRepository = mockk<IssueLabelRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingRepository = mockk<PostingRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()

    val service = MigrationService(
        userRepository = userRepository,
        projectRepository = projectRepository,
        projectUserRepository = projectUserRepository,
        organizationUserRepository = organizationUserRepository,
        issueRepository = issueRepository,
        issueLabelRepository = issueLabelRepository,
        issueCommentRepository = issueCommentRepository,
        postingRepository = postingRepository,
        postingCommentRepository = postingCommentRepository,
        milestoneRepository = milestoneRepository,
        attachmentRepository = attachmentRepository,
        clientId = "test-client-id",
        clientSecret = "test-client-secret",
        allowMigration = true
    )

    beforeTest {
        clearMocks(
            userRepository, projectRepository, projectUserRepository, organizationUserRepository,
            issueRepository, issueLabelRepository, issueCommentRepository, postingRepository,
            postingCommentRepository, milestoneRepository, attachmentRepository,
            answers = false
        )
        // 기본값: 첨부파일 없음. 필요한 테스트에서 특정 (type, id) 조합만 덮어쓴다.
        every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
    }

    describe("MigrationService.isAllowMigration") {
        it("allowMigration 설정값이 true면 true를 반환해야 한다") {
            service.isAllowMigration() shouldBe true
        }

        it("allowMigration 설정값이 false면 false를 반환해야 한다") {
            val disabledService = MigrationService(
                userRepository, projectRepository, projectUserRepository, organizationUserRepository,
                issueRepository, issueLabelRepository, issueCommentRepository, postingRepository,
                postingCommentRepository, milestoneRepository, attachmentRepository,
                clientId = "cid", clientSecret = "secret", allowMigration = false
            )
            disabledService.isAllowMigration() shouldBe false
        }

        it("clientId/clientSecret/allowMigration을 생략하면 기본값(빈 문자열, false)이 적용되어야 한다") {
            // 트레일링 옵션 파라미터를 생략해 Kotlin 기본값 처리 경로(synthetic constructor)를 실행한다.
            val defaultService = MigrationService(
                userRepository, projectRepository, projectUserRepository, organizationUserRepository,
                issueRepository, issueLabelRepository, issueCommentRepository, postingRepository,
                postingCommentRepository, milestoneRepository, attachmentRepository
            )
            defaultService.isAllowMigration() shouldBe false
        }
    }

    describe("MigrationService.getOAuthToken") {
        fun githubResponse(body: Map<String, Any>?): ResponseEntity<Map<*, *>> = ResponseEntity(body, HttpStatus.OK)

        it("GitHub가 access_token을 문자열로 응답하면 그 값을 반환해야 한다") {
            mockkConstructor(RestTemplate::class)
            try {
                every {
                    anyConstructed<RestTemplate>().postForEntity(any<String>(), any(), Map::class.java)
                } returns githubResponse(mapOf("access_token" to "gh-token-abc"))

                service.getOAuthToken("auth-code-1") shouldBe "gh-token-abc"
            } finally {
                unmockkConstructor(RestTemplate::class)
            }
        }

        it("응답 바디가 없으면 빈 문자열을 반환해야 한다") {
            mockkConstructor(RestTemplate::class)
            try {
                every {
                    anyConstructed<RestTemplate>().postForEntity(any<String>(), any(), Map::class.java)
                } returns githubResponse(null)

                service.getOAuthToken("auth-code-2") shouldBe ""
            } finally {
                unmockkConstructor(RestTemplate::class)
            }
        }

        it("응답 바디에 access_token 키가 없으면 빈 문자열을 반환해야 한다") {
            mockkConstructor(RestTemplate::class)
            try {
                every {
                    anyConstructed<RestTemplate>().postForEntity(any<String>(), any(), Map::class.java)
                } returns githubResponse(mapOf("error" to "bad_verification_code"))

                service.getOAuthToken("auth-code-3") shouldBe ""
            } finally {
                unmockkConstructor(RestTemplate::class)
            }
        }

        it("access_token 값이 문자열이 아니면 빈 문자열을 반환해야 한다") {
            mockkConstructor(RestTemplate::class)
            try {
                every {
                    anyConstructed<RestTemplate>().postForEntity(any<String>(), any(), Map::class.java)
                } returns githubResponse(mapOf("access_token" to 12345))

                service.getOAuthToken("auth-code-4") shouldBe ""
            } finally {
                unmockkConstructor(RestTemplate::class)
            }
        }

        it("REST 호출 중 예외가 발생하면 빈 문자열을 반환해야 한다") {
            mockkConstructor(RestTemplate::class)
            try {
                every {
                    anyConstructed<RestTemplate>().postForEntity(any<String>(), any(), Map::class.java)
                } throws RestClientException("network error")

                service.getOAuthToken("auth-code-5") shouldBe ""
            } finally {
                unmockkConstructor(RestTemplate::class)
            }
        }
    }

    describe("MigrationService.getMigrationProjects") {
        it("매니저로 속한 프로젝트와 조직 관리자로 속한 조직의 프로젝트를 모아 owner/name 순으로 정렬해 반환해야 한다") {
            val managerRole = Role(id = RoleType.MANAGER.roleType, name = "manager")
            val memberRole = Role(id = RoleType.MEMBER.roleType, name = "member")
            val orgAdminRole = Role(id = RoleType.ORG_ADMIN.roleType, name = "org_admin")
            val user = User(id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@yona.io")

            val projectA = Project(id = 10L, owner = "alice", name = "proj-b", projectScope = ProjectScope.PUBLIC)
            projectA.projectUsers = mutableListOf(ProjectUser(id = 1L, user = user, project = projectA, role = managerRole))

            val projectB = Project(id = 11L, owner = "alice", name = "proj-a", projectScope = ProjectScope.PRIVATE)

            val projectC = Project(id = 12L, owner = "bob", name = "excluded", projectScope = ProjectScope.PUBLIC)

            val puManagerA = ProjectUser(id = 100L, user = user, project = projectA, role = managerRole)
            val puMemberC = ProjectUser(id = 101L, user = user, project = projectC, role = memberRole)
            every { projectUserRepository.findByUserId(1L) } returns listOf(puManagerA, puMemberC)

            // projectA는 조직 프로젝트 목록에도 중복 포함되어 Set의 중복 제거를 함께 검증한다.
            val org = Organization(id = 5L, name = "org1", projects = mutableListOf(projectB, projectA))
            val orgUser = OrganizationUser(id = 200L, user = user, organization = org, role = orgAdminRole)
            every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns listOf(orgUser)

            val result = service.getMigrationProjects(user)

            result.size shouldBe 2
            result[0]["owner"] shouldBe "alice"
            result[0]["projectName"] shouldBe "proj-a"
            result[0]["private"] shouldBe true
            result[0]["members"] shouldBe 0
            result[0]["full_name"] shouldBe "alice/proj-a"

            result[1]["projectName"] shouldBe "proj-b"
            result[1]["private"] shouldBe false
            result[1]["members"] shouldBe 1
            result[1]["full_name"] shouldBe "alice/proj-b"
        }

        it("매니저 권한도 조직 관리자 권한도 없으면 빈 목록을 반환해야 한다") {
            val user = User(id = 2L, loginId = "member-only", name = "멤버", email = "member@yona.io")
            val memberRole = Role(id = RoleType.MEMBER.roleType, name = "member")
            val someProject = Project(id = 20L, owner = "carol", name = "not-managed")
            val puMember = ProjectUser(id = 300L, user = user, project = someProject, role = memberRole)

            every { projectUserRepository.findByUserId(2L) } returns listOf(puMember)
            every { organizationUserRepository.findByUserIdAndRoleId(2L, RoleType.ORG_ADMIN.roleType) } returns emptyList()

            val result = service.getMigrationProjects(user)

            result.shouldBeEmpty()
        }

        it("owner가 없는 프로젝트는 owner를 빈 문자열로 대체하되 full_name에는 원본 owner 표현이 그대로 노출되어야 한다") {
            val managerRole = Role(id = RoleType.MANAGER.roleType, name = "manager")
            val user = User(id = 3L, loginId = "orphan-owner", name = "무소속", email = "orphan@yona.io")
            val projectNoOwner = Project(id = 30L, owner = null, name = "orphan", projectScope = ProjectScope.PROTECTED)
            val puManager = ProjectUser(id = 400L, user = user, project = projectNoOwner, role = managerRole)

            every { projectUserRepository.findByUserId(3L) } returns listOf(puManager)
            every { organizationUserRepository.findByUserIdAndRoleId(3L, RoleType.ORG_ADMIN.roleType) } returns emptyList()

            val result = service.getMigrationProjects(user)

            result.size shouldBe 1
            result[0]["owner"] shouldBe ""
            // 실제 버그로 보임: owner가 null이어도 "${p.owner}/${p.name}" 문자열 템플릿은 리터럴 "null"을
            // 그대로 포함해 "null/orphan"이 만들어진다("owner" 필드의 "" 대체와 일관되지 않음).
            result[0]["full_name"] shouldBe "null/orphan"
            result[0]["private"] shouldBe true
        }

        it("역할(Role)의 id가 null이면 매니저로 취급하지 않아야 한다") {
            // Role.id는 nullable(Long?)이라 실제로 null인 역할 데이터가 들어올 수 있는 경로를 검증한다.
            val user = User(id = 4L, loginId = "null-role-id", name = "역할없음", email = "x@yona.io")
            val roleWithNullId = Role(id = null, name = "unknown")
            val project = Project(id = 40L, owner = "eve", name = "role-null-id")
            val pu = ProjectUser(id = 500L, user = user, project = project, role = roleWithNullId)

            every { projectUserRepository.findByUserId(4L) } returns listOf(pu)
            every { organizationUserRepository.findByUserIdAndRoleId(4L, RoleType.ORG_ADMIN.roleType) } returns emptyList()

            val result = service.getMigrationProjects(user)

            result.shouldBeEmpty()
        }
    }

    describe("MigrationService.getMigrationProjectDetail") {
        it("존재하지 않는 프로젝트/오너 조합이면 null을 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("owner1", "proj1") } returns Optional.empty()

            service.getMigrationProjectDetail("owner1", "proj1").shouldBeNull()
        }

        it("담당자 목록과 카운트가 채워진 프로젝트 상세 정보를 반환해야 한다") {
            val project = Project(id = 1L, owner = "alice", name = "demo")
            val member = User(id = 2L, name = "철수", loginId = "chulsoo", email = "chulsoo@yona.io")
            project.projectUsers = mutableListOf(
                ProjectUser(id = 1L, user = member, project = project, role = Role(id = 1L, name = "manager"))
            )

            every { projectRepository.findByOwnerAndName("alice", "demo") } returns Optional.of(project)
            every { issueRepository.countByProject(project) } returns 5L
            every { postingRepository.countByProject(project) } returns 3L
            every { milestoneRepository.countByProject(project) } returns 2L

            val result = service.getMigrationProjectDetail("alice", "demo")!!

            result["owner"] shouldBe "alice"
            result["projectName"] shouldBe "demo"
            result["full_name"] shouldBe "alice/demo"
            result["memberCount"] shouldBe 1
            result["issueCount"] shouldBe 5L
            result["postCount"] shouldBe 3L
            result["milestoneCount"] shouldBe 2L

            @Suppress("UNCHECKED_CAST")
            val assignees = result["assignees"] as List<Map<String, Any>>
            assignees.size shouldBe 1
            assignees[0]["name"] shouldBe "철수"
            assignees[0]["login"] shouldBe "chulsoo"
            assignees[0]["email"] shouldBe "chulsoo@yona.io"
        }

        it("owner가 없고 멤버가 없는 프로젝트는 owner 빈 문자열과 빈 담당자 목록을 반환해야 한다") {
            val project = Project(id = 2L, owner = null, name = "noowner")

            every { projectRepository.findByOwnerAndName("", "noowner") } returns Optional.of(project)
            every { issueRepository.countByProject(project) } returns 0L
            every { postingRepository.countByProject(project) } returns 0L
            every { milestoneRepository.countByProject(project) } returns 0L

            val result = service.getMigrationProjectDetail("", "noowner")!!

            result["owner"] shouldBe ""
            result["full_name"] shouldBe "null/noowner"
            result["memberCount"] shouldBe 0
            @Suppress("UNCHECKED_CAST")
            (result["assignees"] as List<Any>).shouldBeEmpty()
        }
    }

    describe("MigrationService.exportLabels") {
        it("존재하지 않는 프로젝트면 null을 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("owner1", "proj1") } returns Optional.empty()

            service.exportLabels("owner1", "proj1").shouldBeNull()
        }

        it("프로젝트의 라벨 목록을 id를 key로 하는 맵으로 반환해야 한다") {
            val project = Project(id = 1L, owner = "alice", name = "demo")
            val category = IssueLabelCategory(id = 1L, name = "type", project = project)
            val label1 = IssueLabel(id = 10L, category = category, color = "#fff", name = "bug", project = project)
            val label2 = IssueLabel(id = 11L, category = category, color = "#000", name = "feature", project = project)

            every { projectRepository.findByOwnerAndName("alice", "demo") } returns Optional.of(project)
            every { issueLabelRepository.findByProject(project) } returns listOf(label1, label2)

            val result = service.exportLabels("alice", "demo")!!

            @Suppress("UNCHECKED_CAST")
            val labels = result["labels"] as Map<String, Map<String, Any>>
            labels.size shouldBe 2
            labels["10"]!!["name"] shouldBe "bug"
            labels["10"]!!["categoryId"] shouldBe 1L
            labels["10"]!!["categoryName"] shouldBe "type"
            labels["11"]!!["name"] shouldBe "feature"
        }
    }

    describe("MigrationService.exportIssueLabelPairs") {
        it("존재하지 않는 프로젝트면 null을 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("owner1", "proj1") } returns Optional.empty()

            service.exportIssueLabelPairs("owner1", "proj1").shouldBeNull()
        }

        it("이슈가 없으면 빈 목록을 반환해야 한다") {
            val project = Project(id = 1L, owner = "alice", name = "demo")
            every { projectRepository.findByOwnerAndName("alice", "demo") } returns Optional.of(project)
            every { issueRepository.findByProject(project) } returns emptyList()

            val result = service.exportIssueLabelPairs("alice", "demo")!!

            @Suppress("UNCHECKED_CAST")
            (result["issueLabelPairs"] as List<Any>).shouldBeEmpty()
        }

        it("라벨이 없는 이슈는 건너뛰고 라벨이 있는 이슈만 issue_id/issue_label_id 쌍으로 반환해야 한다") {
            val project = Project(id = 1L, owner = "alice", name = "demo")
            val category = IssueLabelCategory(id = 1L, name = "type", project = project)
            val label1 = IssueLabel(id = 10L, category = category, color = "#fff", name = "bug", project = project)
            val label2 = IssueLabel(id = 11L, category = category, color = "#000", name = "feature", project = project)

            val issueNoLabel = Issue(id = 100L, title = "no-label", project = project, labels = mutableSetOf())
            val issueWithLabels = Issue(
                id = 101L, title = "with-labels", project = project,
                labels = mutableSetOf(label1, label2)
            )

            every { projectRepository.findByOwnerAndName("alice", "demo") } returns Optional.of(project)
            every { issueRepository.findByProject(project) } returns listOf(issueNoLabel, issueWithLabels)

            val result = service.exportIssueLabelPairs("alice", "demo")!!

            @Suppress("UNCHECKED_CAST")
            val pairs = result["issueLabelPairs"] as List<Map<String, Any>>
            pairs.size shouldBe 2
            pairs.map { it["issue_id"] }.toSet() shouldBe setOf(101L)
            pairs.map { it["issue_label_id"] }.toSet() shouldBe setOf(10L, 11L)
        }

        it("이슈 또는 라벨의 id가 null이면 0으로 대체해 반환해야 한다") {
            // Issue.id/IssueLabel.id는 모두 nullable(Long?, @GeneratedValue 이전 상태)이라
            // 저장 전 엔티티가 섞여 들어오는 방어 코드(?: 0L) 경로를 검증한다.
            val project = Project(id = 1L, owner = "alice", name = "demo")
            val category = IssueLabelCategory(id = 1L, name = "type", project = project)
            val labelNullId = IssueLabel(id = null, category = category, color = "#fff", name = "no-id-label", project = project)
            val issueNullId = Issue(id = null, title = "no-id-issue", project = project, labels = mutableSetOf(labelNullId))

            every { projectRepository.findByOwnerAndName("alice", "demo") } returns Optional.of(project)
            every { issueRepository.findByProject(project) } returns listOf(issueNullId)

            val result = service.exportIssueLabelPairs("alice", "demo")!!

            @Suppress("UNCHECKED_CAST")
            val pairs = result["issueLabelPairs"] as List<Map<String, Any>>
            pairs.size shouldBe 1
            pairs[0]["issue_id"] shouldBe 0L
            pairs[0]["issue_label_id"] shouldBe 0L
        }
    }

    describe("MigrationService.exportMilestones") {
        it("존재하지 않는 프로젝트면 null을 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("owner1", "proj1") } returns Optional.empty()

            service.exportMilestones("owner1", "proj1").shouldBeNull()
        }

        it("dueDate가 있는 마일스톤과 없는 마일스톤을 구분해 반환해야 한다") {
            val project = Project(id = 1L, owner = "alice", name = "demo")
            val due = Instant.parse("2026-01-15T00:00:00Z")
            val m1 = Milestone(id = 1L, title = "v1", dueDate = due, contents = "설명", state = State.OPEN, project = project)
            val m2 = Milestone(id = 2L, title = "v2", dueDate = null, contents = null, state = State.CLOSED, project = project)

            every { projectRepository.findByOwnerAndName("alice", "demo") } returns Optional.of(project)
            every { milestoneRepository.findByProject(project) } returns listOf(m1, m2)

            val result = service.exportMilestones("alice", "demo")!!

            result.size shouldBe 2

            @Suppress("UNCHECKED_CAST")
            val node1 = result[0]["milestone"] as Map<String, Any?>
            node1["id"] shouldBe 1L
            node1["title"] shouldBe "v1"
            node1["state"] shouldBe "open"
            node1["description"] shouldBe "설명"
            node1["due_on"] shouldBe formatter.format(due)

            @Suppress("UNCHECKED_CAST")
            val node2 = result[1]["milestone"] as Map<String, Any?>
            node2["state"] shouldBe "closed"
            node2["description"].shouldBeNull()
            node2.containsKey("due_on") shouldBe false
        }
    }

    describe("MigrationService.exportIssues") {
        it("존재하지 않는 프로젝트면 null을 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("owner1", "proj1") } returns Optional.empty()

            service.exportIssues("owner1", "proj1", withWikiCommit = false).shouldBeNull()
        }

        it("모든 필드가 채워진 이슈를 withWikiCommit=false로 내보내면 절대경로 링크와 첨부파일 목록이 포함되어야 한다") {
            val project = Project(id = 1L, owner = "alice", name = "demo")
            val milestone = Milestone(id = 5L, title = "1.0", project = project)
            val assigneeUser = User(id = 9L, loginId = "assignee-login", name = "담당자")
            val assignee = Assignee(id = 1L, user = assigneeUser, project = project)
            val createdAt = Instant.parse("2026-01-01T00:00:00Z")
            val bodyText = "이슈 본문 <img src='/uploads/img.png'> 그리고 [문서](/wiki/abc)"
            val issue = Issue(
                id = 100L, title = "버그입니다", body = bodyText, project = project, number = 7L,
                authorLoginId = "author1", authorName = "작성자", createdDate = createdAt,
                assignee = assignee, milestone = milestone, state = State.CLOSED
            )

            val commentCreatedAt = Instant.parse("2026-01-02T00:00:00Z")
            val commentBody = "댓글 본문 <img src='/uploads/c.png'> [링크](/board/1)"
            val comment = IssueComment(
                id = 500L, contents = commentBody, createdDate = commentCreatedAt,
                authorLoginId = "commenter1", authorName = "댓글러", issue = issue
            )

            every { projectRepository.findByOwnerAndName("alice", "demo") } returns Optional.of(project)
            every { issueRepository.findByProject(project) } returns listOf(issue)
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(100L) } returns listOf(comment)

            val issueAttachment1 = Attachment(id = 900L, name = "spec.pdf", hash = "h1", containerType = ResourceType.ISSUE_POST, containerId = "100")
            val issueAttachment2 = Attachment(id = 901L, name = "diagram.png", hash = "h2", containerType = ResourceType.ISSUE_POST, containerId = "100")
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, "100") } returns listOf(issueAttachment1, issueAttachment2)

            val result = service.exportIssues("alice", "demo", withWikiCommit = false)!!

            result.size shouldBe 1
            @Suppress("UNCHECKED_CAST")
            val node = result[0]["issue"] as Map<String, Any?>
            node["id"] shouldBe 100L
            node["title"] shouldBe "버그입니다"
            val body = node["body"] as String
            body shouldContain "@author1 (작성자) 님이 작성한 [이슈](/alice/demo/issue/7)입니다."
            body shouldContain "<img src='/uploads/img.png'>"
            body shouldContain "[문서](/wiki/abc)"
            body shouldContain "--- attachments ---"
            body shouldContain "[spec.pdf](/files/900)"
            body shouldContain "[diagram.png](/files/901)"
            node["created_at"] shouldBe formatter.format(createdAt)
            node["assignee"] shouldBe "assignee-login"
            node["milestone"] shouldBe "1.0"
            node["milestoneId"] shouldBe 5L
            node["closed"] shouldBe true

            @Suppress("UNCHECKED_CAST")
            val comments = result[0]["comments"] as List<Map<String, Any?>>
            comments.size shouldBe 1
            val commentNode = comments[0]
            commentNode["created_at"] shouldBe formatter.format(commentCreatedAt)
            val cBody = commentNode["body"] as String
            cBody shouldContain "@commenter1 (댓글러) 님이 작성한 [코멘트](/alice/demo/issue/7#comment-500)입니다."
            cBody shouldContain "<img src='/uploads/c.png'>"
            cBody shouldContain "[링크](/board/1)"
            cBody shouldNotContain "--- attachments ---"
        }

        it("모든 필드가 비어있는 이슈를 withWikiCommit=false로 내보내면 빈 문자열/누락 키로 처리되어야 한다") {
            val project = Project(id = 3L, owner = "carol", name = "emptyproj")
            val issue = Issue(
                id = 300L, title = "빈 이슈", body = null, project = project, number = 1L,
                authorLoginId = null, authorName = null, createdDate = null,
                assignee = null, milestone = null, state = State.OPEN
            )
            val comment = IssueComment(
                id = 700L, contents = "", createdDate = null,
                authorLoginId = null, authorName = null, issue = issue
            )

            every { projectRepository.findByOwnerAndName("carol", "emptyproj") } returns Optional.of(project)
            every { issueRepository.findByProject(project) } returns listOf(issue)
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(300L) } returns listOf(comment)

            val result = service.exportIssues("carol", "emptyproj", withWikiCommit = false)!!

            @Suppress("UNCHECKED_CAST")
            val node = result[0]["issue"] as Map<String, Any?>
            val body = node["body"] as String
            body shouldContain "@ () 님이 작성한 [이슈](/carol/emptyproj/issue/1)입니다."
            node.containsKey("created_at") shouldBe false
            node["assignee"].shouldBeNull()
            node["milestone"].shouldBeNull()
            node["milestoneId"].shouldBeNull()
            node["closed"] shouldBe false

            @Suppress("UNCHECKED_CAST")
            val comments = result[0]["comments"] as List<Map<String, Any?>>
            comments[0].containsKey("created_at") shouldBe false
        }

        it("모든 필드가 채워진 이슈를 withWikiCommit=true로 내보내면 위키 커밋 경로와 첨부파일 목록이 포함되어야 한다") {
            val project = Project(id = 2L, owner = "bob", name = "wikiproj")
            val milestone = Milestone(id = 6L, title = "2.0", project = project)
            val assigneeUser = User(id = 19L, loginId = "wiki-assignee", name = "위키담당자")
            val assignee = Assignee(id = 2L, user = assigneeUser, project = project)
            val createdAt = Instant.parse("2026-02-01T00:00:00Z")
            val bodyText = "위키 본문 <img src='/uploads/wiki.png'> 그리고 [문서](/wiki/abc)"
            val issue = Issue(
                id = 200L, title = "위키 이슈", body = bodyText, project = project, number = 3L,
                authorLoginId = "wiki-author", authorName = "위키작성자", createdDate = createdAt,
                assignee = assignee, milestone = milestone, state = State.OPEN
            )

            val commentCreatedAt = Instant.parse("2026-02-02T00:00:00Z")
            val commentBody = "위키 댓글 <img src='/uploads/wc.png'> [댓글링크](/c/1)"
            val comment = IssueComment(
                id = 600L, contents = commentBody, createdDate = commentCreatedAt,
                authorLoginId = "wiki-commenter", authorName = "위키댓글러", issue = issue
            )

            every { projectRepository.findByOwnerAndName("bob", "wikiproj") } returns Optional.of(project)
            every { issueRepository.findByProject(project) } returns listOf(issue)
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(200L) } returns listOf(comment)

            val commentAttachment = Attachment(id = 950L, name = "파일#1.png", hash = "h3", containerType = ResourceType.ISSUE_COMMENT, containerId = "600")
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_COMMENT, "600") } returns listOf(commentAttachment)

            val result = service.exportIssues("bob", "wikiproj", withWikiCommit = true)!!

            @Suppress("UNCHECKED_CAST")
            val node = result[0]["issue"] as Map<String, Any?>
            val body = node["body"] as String
            body shouldContain "@wiki-author (위키작성자) 님이 작성한 [이슈](/bob/wikiproj/issue/3)입니다."
            // 실제 버그로 보임: relativeLinksToWikiCommitPath의 마크다운 링크 치환 람다가 매치별
            // 상대경로가 아니라 함수 인자로 받은 "전체 원본 텍스트"를 그대로 위키 경로 뒤에 이어붙인다.
            body shouldContain "[문서](../wiki/wiki/abc/$bodyText)"
            body shouldNotContain "--- attachments ---"

            @Suppress("UNCHECKED_CAST")
            val comments = result[0]["comments"] as List<Map<String, Any?>>
            val cBody = comments[0]["body"] as String
            cBody shouldContain "[댓글링크](../wiki/c/1/$commentBody)"
            cBody shouldContain "--- attachments ---"
            cBody shouldContain "[파일#1.png](../wiki/files/950/파일%231.png)"
            node["assignee"] shouldBe "wiki-assignee"
            node["milestone"] shouldBe "2.0"
            node["closed"] shouldBe false
        }

        it("모든 필드가 비어있는 이슈를 withWikiCommit=true로 내보내면 빈 문자열/누락 키로 처리되어야 한다") {
            val project = Project(id = 4L, owner = "dave", name = "wikiempty")
            val issue = Issue(
                id = 400L, title = "빈 위키 이슈", body = null, project = project, number = 2L,
                authorLoginId = null, authorName = null, createdDate = null,
                assignee = null, milestone = null, state = State.OPEN
            )
            val comment = IssueComment(
                id = 800L, contents = "", createdDate = null,
                authorLoginId = null, authorName = null, issue = issue
            )

            every { projectRepository.findByOwnerAndName("dave", "wikiempty") } returns Optional.of(project)
            every { issueRepository.findByProject(project) } returns listOf(issue)
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(400L) } returns listOf(comment)

            val result = service.exportIssues("dave", "wikiempty", withWikiCommit = true)!!

            @Suppress("UNCHECKED_CAST")
            val node = result[0]["issue"] as Map<String, Any?>
            val body = node["body"] as String
            body shouldContain "@ () 님이 작성한 [이슈](/dave/wikiempty/issue/2)입니다."
            node.containsKey("created_at") shouldBe false
            node["assignee"].shouldBeNull()
            node["milestone"].shouldBeNull()
        }
    }

    describe("MigrationService.exportPosts") {
        it("존재하지 않는 프로젝트면 null을 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("owner1", "proj1") } returns Optional.empty()

            service.exportPosts("owner1", "proj1", withWikiCommit = false).shouldBeNull()
        }

        it("모든 필드가 채워진 게시글을 withWikiCommit=false로 내보내면 절대경로 링크와 첨부파일 목록이 포함되어야 한다") {
            val project = Project(id = 1L, owner = "alice", name = "board-demo")
            val createdAt = Instant.parse("2026-03-01T00:00:00Z")
            val bodyText = "게시글 본문 <img src='/uploads/post.png'> 그리고 [문서](/wiki/xyz)"
            val post = Posting(
                id = 100L, title = "공지사항", body = bodyText, project = project, number = 9L,
                authorLoginId = "author1", authorName = "작성자", createdDate = createdAt
            )

            val commentCreatedAt = Instant.parse("2026-03-02T00:00:00Z")
            val commentBody = "댓글 본문 <img src='/uploads/pc.png'> [링크](/board/2)"
            val comment = PostingComment(
                id = 500L, contents = commentBody, createdDate = commentCreatedAt,
                authorLoginId = "commenter1", authorName = "댓글러", posting = post
            )

            every { projectRepository.findByOwnerAndName("alice", "board-demo") } returns Optional.of(project)
            every { postingRepository.findByProject(project) } returns listOf(post)
            every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(100L) } returns listOf(comment)

            val postAttachment = Attachment(id = 900L, name = "attachment.pdf", hash = "h1", containerType = ResourceType.BOARD_POST, containerId = "100")
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "100") } returns listOf(postAttachment)

            val result = service.exportPosts("alice", "board-demo", withWikiCommit = false)!!

            result.size shouldBe 1
            // 실제 구현 그대로: exportPosts도 exportIssues와 동일하게 "issue" 키를 재사용한다
            // (게시글 전용 키가 아니라 이슈 export 코드를 복붙한 흔적으로 보인다).
            @Suppress("UNCHECKED_CAST")
            val node = result[0]["issue"] as Map<String, Any?>
            node["title"] shouldBe "공지사항"
            node.containsKey("id") shouldBe false
            val body = node["body"] as String
            body shouldContain "@author1 (작성자) 님이 작성한 [게시글](/alice/board-demo/post/9)입니다."
            body shouldContain "<img src='/uploads/post.png'>"
            body shouldContain "--- attachments ---"
            body shouldContain "[attachment.pdf](/files/900)"
            node["created_at"] shouldBe formatter.format(createdAt)

            @Suppress("UNCHECKED_CAST")
            val comments = result[0]["comments"] as List<Map<String, Any?>>
            comments.size shouldBe 1
            val cBody = comments[0]["body"] as String
            cBody shouldContain "@commenter1 (댓글러) 님이 작성한 [코멘트](/alice/board-demo/post/9#comment-500)입니다."
            cBody shouldNotContain "--- attachments ---"
        }

        it("모든 필드가 비어있는 게시글을 withWikiCommit=false로 내보내면 빈 문자열/누락 키로 처리되어야 한다") {
            val project = Project(id = 3L, owner = "carol", name = "board-empty")
            val post = Posting(
                id = 300L, title = "빈 게시글", body = null, project = project, number = 1L,
                authorLoginId = null, authorName = null, createdDate = null
            )
            val comment = PostingComment(
                id = 700L, contents = "", createdDate = null,
                authorLoginId = null, authorName = null, posting = post
            )

            every { projectRepository.findByOwnerAndName("carol", "board-empty") } returns Optional.of(project)
            every { postingRepository.findByProject(project) } returns listOf(post)
            every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(300L) } returns listOf(comment)

            val result = service.exportPosts("carol", "board-empty", withWikiCommit = false)!!

            @Suppress("UNCHECKED_CAST")
            val node = result[0]["issue"] as Map<String, Any?>
            val body = node["body"] as String
            body shouldContain "@ () 님이 작성한 [게시글](/carol/board-empty/post/1)입니다."
            node.containsKey("created_at") shouldBe false

            @Suppress("UNCHECKED_CAST")
            val comments = result[0]["comments"] as List<Map<String, Any?>>
            comments[0].containsKey("created_at") shouldBe false
        }

        it("모든 필드가 채워진 게시글을 withWikiCommit=true로 내보내면 위키 커밋 경로와 첨부파일 목록이 포함되어야 한다") {
            val project = Project(id = 2L, owner = "bob", name = "board-wiki")
            val createdAt = Instant.parse("2026-04-01T00:00:00Z")
            val bodyText = "위키 게시글 본문 <img src='/uploads/wpost.png'> 그리고 [문서](/wiki/xyz)"
            val post = Posting(
                id = 200L, title = "위키 게시글", body = bodyText, project = project, number = 4L,
                authorLoginId = "wiki-author", authorName = "위키작성자", createdDate = createdAt
            )

            val commentCreatedAt = Instant.parse("2026-04-02T00:00:00Z")
            val commentBody = "위키 댓글 <img src='/uploads/wpc.png'> [댓글링크](/c/2)"
            val comment = PostingComment(
                id = 600L, contents = commentBody, createdDate = commentCreatedAt,
                authorLoginId = "wiki-commenter", authorName = "위키댓글러", posting = post
            )

            every { projectRepository.findByOwnerAndName("bob", "board-wiki") } returns Optional.of(project)
            every { postingRepository.findByProject(project) } returns listOf(post)
            every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(200L) } returns listOf(comment)

            val commentAttachment = Attachment(id = 950L, name = "파일#2.png", hash = "h3", containerType = ResourceType.NONISSUE_COMMENT, containerId = "600")
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.NONISSUE_COMMENT, "600") } returns listOf(commentAttachment)

            val result = service.exportPosts("bob", "board-wiki", withWikiCommit = true)!!

            @Suppress("UNCHECKED_CAST")
            val node = result[0]["issue"] as Map<String, Any?>
            val body = node["body"] as String
            body shouldContain "@wiki-author (위키작성자) 님이 작성한 [게시글](/bob/board-wiki/post/4)입니다."
            body shouldContain "[문서](../wiki/wiki/xyz/$bodyText)"
            body shouldNotContain "--- attachments ---"

            @Suppress("UNCHECKED_CAST")
            val comments = result[0]["comments"] as List<Map<String, Any?>>
            val cBody = comments[0]["body"] as String
            cBody shouldContain "[댓글링크](../wiki/c/2/$commentBody)"
            cBody shouldContain "--- attachments ---"
            cBody shouldContain "[파일#2.png](../wiki/files/950/파일%232.png)"
        }

        it("모든 필드가 비어있는 게시글을 withWikiCommit=true로 내보내면 빈 문자열/누락 키로 처리되어야 한다") {
            val project = Project(id = 4L, owner = "dave", name = "board-wiki-empty")
            val post = Posting(
                id = 400L, title = "빈 위키 게시글", body = null, project = project, number = 2L,
                authorLoginId = null, authorName = null, createdDate = null
            )
            val comment = PostingComment(
                id = 800L, contents = "", createdDate = null,
                authorLoginId = null, authorName = null, posting = post
            )

            every { projectRepository.findByOwnerAndName("dave", "board-wiki-empty") } returns Optional.of(project)
            every { postingRepository.findByProject(project) } returns listOf(post)
            every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(400L) } returns listOf(comment)

            val result = service.exportPosts("dave", "board-wiki-empty", withWikiCommit = true)!!

            @Suppress("UNCHECKED_CAST")
            val node = result[0]["issue"] as Map<String, Any?>
            val body = node["body"] as String
            body shouldContain "@ () 님이 작성한 [게시글](/dave/board-wiki-empty/post/2)입니다."
            node.containsKey("created_at") shouldBe false
        }
    }
})
