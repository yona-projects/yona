package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueEvent
import com.github.yonaprojects.yona.domain.issue.IssueEventRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEvent
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEventRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.time.Instant

// yona partial_event_timeline.scala.html/partial_pull_request_event.scala.html 대응 (P1-106).
// 단위 테스트(mockk)는 모델 데이터 조립만 검증하고 실제 Thymeleaf 렌더링은 거치지 않으므로,
// 이 스펙은 실제 ViewResolver를 태워 템플릿이 문법 오류 없이 렌더링되고 이벤트 메시지가 실제
// HTML 응답에 나타나는지까지 확인한다.
@Transactional
class TimelineTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val issueEventRepository: IssueEventRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val pullRequestEventRepository: PullRequestEventRepository
) : AbstractIntegrationTest() {

    private val mockMvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(webApplicationContext).build() }

    init {
        describe("이슈/PR 상세 화면 타임라인 렌더링") {
            // 이 스펙은 이름이 고유한(tmpl- 접두) 프로젝트/유저만 만들고, 클래스에 붙은 @Transactional이
            // 각 테스트 종료 시 롤백을 보장하므로 다른 스펙의 데이터를 건드리는 전역 deleteAll()은
            // 쓰지 않는다(공유 테스트 DB에서 무관한 프로젝트를 지우다 FK 위반이 날 수 있음).

            it("이슈 상세 화면이 상태변경 이벤트 메시지를 실제로 렌더링해야 한다") {
                val author = userRepository.save(User(loginId = "tmpl-author", name = "템플릿작성자", email = "tmpl-author@yona.io"))
                val project = projectRepository.save(Project(name = "tmpl-proj", owner = "tmpl-owner", projectScope = ProjectScope.PUBLIC))
                val issue = issueRepository.save(
                    Issue(title = "템플릿 테스트 이슈", body = "본문", project = project, number = 1L, authorId = author.id)
                )
                issueCommentRepository.save(
                    IssueComment(contents = "테스트 댓글", issue = issue, authorLoginId = author.loginId, createdDate = Instant.now())
                )
                issueEventRepository.save(
                    IssueEvent(
                        issue = issue, senderLoginId = author.loginId,
                        eventType = EventType.ISSUE_STATE_CHANGED,
                        oldValue = "OPEN", newValue = "CLOSED", created = Instant.now()
                    )
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issue/1"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "이 이슈를 닫았습니다"
            }

            it("PR 상세 화면이 리뷰완료 이벤트 메시지를 실제로 렌더링해야 한다") {
                val author = userRepository.save(User(loginId = "tmpl-author2", name = "PR작성자", email = "tmpl-author2@yona.io"))
                val project = projectRepository.save(Project(name = "tmpl-proj2", owner = "tmpl-owner2", projectScope = ProjectScope.PUBLIC))
                val pullRequest = pullRequestRepository.save(
                    PullRequest(
                        title = "템플릿 테스트 PR", body = "PR 본문",
                        toProject = project, fromProject = project,
                        toBranch = "master", fromBranch = "feature",
                        contributor = author, state = State.OPEN, number = 1L
                    )
                )
                pullRequestEventRepository.save(
                    PullRequestEvent(
                        pullRequest = pullRequest, senderLoginId = author.loginId,
                        eventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED,
                        oldValue = "CANCEL", newValue = "DONE", created = Instant.now()
                    )
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/pull/1"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "리뷰를 완료했습니다"
            }
        }
    }
}
