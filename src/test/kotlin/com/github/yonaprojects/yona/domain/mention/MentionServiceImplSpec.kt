package com.github.yonaprojects.yona.domain.mention

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

// yona models/Mention.java 대응 (P2-41, 사용자 지시로 yona의 로직·구조·한계를 그대로 포팅).
@Transactional
class MentionServiceImplSpec @Autowired constructor(
    private val mentionService: MentionService,
    private val mentionRepository: MentionRepository,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository
) : AbstractIntegrationTest() {

    init {
        describe("MentionService 통합 테스트 (P2-41)") {
            beforeEach {
                mentionRepository.deleteAll()
            }

            // yona Mention.java:33-49 update()의 diff-sync 대응. [GL-models_Mention-004;GL-models_Mention-005;GL-models_Mention-006;GL-models_Mention-007;GL-models_Mention-008]
            it("1. update()는 새 멘션은 추가하고 빠진 멘션은 삭제하는 diff-sync여야 한다") {
                val userA = userRepository.save(User(loginId = "mention-a", name = "A"))
                val userB = userRepository.save(User(loginId = "mention-b", name = "B"))

                mentionService.update(ResourceType.ISSUE_POST, "100", setOf(userA, userB))
                mentionRepository.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, "100")
                    .map { it.user.id } shouldContainExactlyInAnyOrder listOf(userA.id, userB.id)

                val userC = userRepository.save(User(loginId = "mention-c", name = "C"))
                mentionService.update(ResourceType.ISSUE_POST, "100", setOf(userB, userC))

                mentionRepository.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, "100")
                    .map { it.user.id } shouldContainExactlyInAnyOrder listOf(userB.id, userC.id)
            }

            it("2. 동일한 멘션 집합으로 다시 update()해도 행 개수가 늘어나지 않아야 한다") {
                val user = userRepository.save(User(loginId = "mention-idem", name = "Idem"))

                mentionService.update(ResourceType.ISSUE_POST, "200", setOf(user))
                mentionService.update(ResourceType.ISSUE_POST, "200", setOf(user))

                mentionRepository.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, "200").size shouldBe 1
            }

            // yona Mention.java:51-72 getMentioningIssueIds() 대응 — ISSUE_POST 멘션과 ISSUE_COMMENT
            // 멘션(부모 이슈 id로 치환)을 합쳐 반환해야 한다.
            it("3. getMentioningIssueIds()는 이슈 본문 멘션과 댓글 멘션(부모 이슈로 치환)을 합쳐 반환해야 한다") {
                val project = projectRepository.save(Project(name = "mention-project", owner = "mention-owner"))
                val user = userRepository.save(User(loginId = "mention-target", name = "Target"))

                val issue1 = issueRepository.save(
                    Issue(title = "직접 본문 멘션 이슈", body = "@mention-target", project = project, number = 1L, state = State.OPEN)
                )
                val issue2 = issueRepository.save(
                    Issue(title = "댓글 멘션 이슈", body = "본문", project = project, number = 2L, state = State.OPEN)
                )
                val comment = issueCommentRepository.save(
                    IssueComment(contents = "@mention-target", createdDate = Instant.now(), issue = issue2)
                )

                mentionService.update(ResourceType.ISSUE_POST, issue1.id.toString(), setOf(user))
                mentionService.update(ResourceType.ISSUE_COMMENT, comment.id.toString(), setOf(user))

                mentionService.getMentioningIssueIds(user.id!!) shouldContainExactlyInAnyOrder listOf(issue1.id, issue2.id)
            }

            it("4. 멘션되지 않은 사용자는 빈 목록을 받아야 한다") {
                val user = userRepository.save(User(loginId = "mention-none", name = "None"))

                mentionService.getMentioningIssueIds(user.id!!) shouldBe emptyList()
            }

            // getMentioningIssueIds()의 resourceId.toLongOrNull()?.let{} — resourceId가 숫자가 아니면
            // 무시하고 건너뛰어야 한다(방어 코드, 정상 흐름에서는 항상 숫자 문자열이 저장됨).
            it("5. resourceId가 숫자가 아닌 멘션 행은 무시해야 한다") {
                val user = userRepository.save(User(loginId = "mention-badid", name = "BadId"))
                mentionRepository.save(Mention(resourceType = ResourceType.ISSUE_POST, resourceId = "not-a-number", user = user))

                mentionService.getMentioningIssueIds(user.id!!) shouldBe emptyList()
            }

            // ISSUE_COMMENT 쪽의 resourceId.toLongOrNull()?.let{} null 분기 — 위 5번 테스트는
            // ISSUE_POST쪽만 커버했으므로 별도로 확인한다.
            it("6. 댓글 멘션의 resourceId가 숫자가 아니면 무시해야 한다") {
                val user = userRepository.save(User(loginId = "mention-badcommentid", name = "BadCommentId"))
                mentionRepository.save(Mention(resourceType = ResourceType.ISSUE_COMMENT, resourceId = "not-a-number", user = user))

                mentionService.getMentioningIssueIds(user.id!!) shouldBe emptyList()
            }
        }
    }
}
