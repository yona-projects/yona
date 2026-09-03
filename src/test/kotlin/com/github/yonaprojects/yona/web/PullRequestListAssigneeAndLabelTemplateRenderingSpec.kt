package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategoryRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// yona-wiki P3-02 Step8.7 2번 — PR 목록 화면(pullrequest/partial_list.html)에도 담당자/라벨을
// Issue 목록(issue/partial_list.html)과 동일한 방식(읽기 전용 배지/아바타, 목록에서는 편집하지
// 않음 - Issue 목록도 편집 UI가 없다)으로 표시한다.
@Transactional
class PullRequestListAssigneeAndLabelTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val issueLabelRepository: IssueLabelRepository
) : AbstractIntegrationTest() {

    private val mockMvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(webApplicationContext).build() }

    init {
        describe("PR 목록 화면의 담당자/라벨 표시") {
            it("목록의 각 PR 항목에 배정된 담당자 아바타와 라벨 배지가 표시되어야 한다") {
                val suffix = System.currentTimeMillis().toString()
                val contributor = userRepository.save(User(loginId = "prl-contributor-$suffix", name = "기여자", email = "prl-contributor-$suffix@yona.io"))
                val assignee = userRepository.save(User(loginId = "prl-assignee-$suffix", name = "목록담당자", email = "prl-assignee-$suffix@yona.io"))
                val project = projectRepository.save(Project(name = "prl-repo-$suffix", owner = "prl-owner-$suffix", projectScope = ProjectScope.PUBLIC))
                projectUserRepository.save(ProjectUser(user = contributor, project = project, role = Role(id = RoleType.MANAGER.roleType)))

                val category = issueLabelCategoryRepository.save(IssueLabelCategory(name = "타입", project = project))
                val label = issueLabelRepository.save(IssueLabel(category = category, project = project, name = "목록라벨", color = "abcdef"))

                pullRequestRepository.save(
                    PullRequest(
                        title = "목록 UI 테스트", body = "본문",
                        toProject = project, fromProject = project,
                        toBranch = "master", fromBranch = "feature",
                        contributor = contributor, state = State.OPEN, number = 1L,
                        assignee = Assignee(user = assignee, project = project),
                        labels = mutableSetOf(label)
                    )
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/pulls"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                val doc = Jsoup.parse(body)
                doc.select("a.avatar-wrap.assignee").isEmpty() shouldBe false
                doc.select("a.label.issue-label[data-label-id='${label.id}']").isEmpty() shouldBe false
            }
        }
    }
}
