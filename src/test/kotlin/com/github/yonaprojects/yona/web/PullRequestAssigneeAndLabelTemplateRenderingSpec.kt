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
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// yona-wiki P3-02 Step8.7 2번 — 7라운드가 백엔드(PullRequestService.setAssignee/addLabel/
// removeLabel, PullRequestApiController)만 구현하고 화면(pullrequest/view.html)은 명시적으로
// 범위 밖에 뒀다. 이슈 상세 화면(issue/view.html)의 담당자/라벨 UI를 최대한 그대로 재사용해
// PR 상세 화면에도 같은 패턴으로 보기+편집 UI를 추가한다(범위: 상세 화면만, 목록/생성폼 제외).
@Transactional
class PullRequestAssigneeAndLabelTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val issueLabelRepository: IssueLabelRepository
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("PR 상세 화면의 담당자/라벨 표시·편집 UI") {
            // 이 스펙은 이름이 고유한(pra- 접두) 프로젝트/유저만 만들고, 클래스에 붙은 @Transactional이
            // 각 테스트 종료 시 롤백을 보장하므로 공유 테스트 DB의 다른 스펙 데이터를 건드리지 않는다.

            fun authOf(u: User) = user(
                YonaUserDetails(
                    id = u.id ?: 0L,
                    loginId = u.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )
            )

            it("프로젝트 멤버는 담당자 선택 드롭다운과 라벨 체크박스를 볼 수 있어야 한다") {
                val suffix = System.currentTimeMillis().toString()
                val manager = userRepository.save(User(loginId = "pra-manager-$suffix", name = "PR매니저", email = "pra-manager-$suffix@yona.io"))
                val project = projectRepository.save(Project(name = "pra-repo-$suffix", owner = "pra-owner-$suffix", projectScope = ProjectScope.PUBLIC))
                // @Transactional 스펙은 setup~mockMvc 호출이 같은 영속성 컨텍스트를 공유해, project
                // 엔티티가 이미 관리 중인(managed) 상태로 재조회돼도 project.projectUsers(양방향
                // OneToMany, mappedBy) 인메모리 컬렉션이 DB의 새 자식 row를 자동 반영하지 않는다
                // (동일 세션 내 identity map 재사용 - 새 트랜잭션/세션에서 다시 조회해야 반영되는데,
                // 이 테스트는 실제로 그렇게 하지 않는다). 템플릿이 project.projectUsers를 직접
                // 순회하므로 저장 후 인메모리 컬렉션에도 명시로 반영해준다.
                project.projectUsers.add(projectUserRepository.save(ProjectUser(user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))))

                val category = issueLabelCategoryRepository.save(IssueLabelCategory(name = "타입", project = project))
                val label = issueLabelRepository.save(IssueLabel(category = category, project = project, name = "버그", color = "ff0000"))

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "PR UI 테스트", body = "본문",
                        toProject = project, fromProject = project,
                        toBranch = "master", fromBranch = "feature",
                        contributor = manager, state = State.OPEN, number = 1L
                    )
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/${pr.number}").with(authOf(manager))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "id=\"pr-assignee-select\""
                val doc = Jsoup.parse(body)
                doc.select("#pr-assignee-select option[value='${manager.id}']").isEmpty() shouldBe false
                doc.select("#labelIds option[value='${label.id}']").isEmpty() shouldBe false
                body shouldContain "버그"
            }

            it("이미 배정된 담당자와 붙은 라벨이 선택된 상태로 렌더링되어야 한다") {
                val suffix = System.currentTimeMillis().toString() + "-2"
                val manager = userRepository.save(User(loginId = "pra-manager2-$suffix", name = "PR매니저2", email = "pra-manager2-$suffix@yona.io"))
                val assignee = userRepository.save(User(loginId = "pra-assignee-$suffix", name = "담당자", email = "pra-assignee-$suffix@yona.io"))
                val project = projectRepository.save(Project(name = "pra-repo2-$suffix", owner = "pra-owner2-$suffix", projectScope = ProjectScope.PUBLIC))
                // 위 테스트와 동일한 이유(@Transactional 세션 내 identity map 재사용)로 인메모리
                // project.projectUsers에도 명시로 반영한다.
                project.projectUsers.add(projectUserRepository.save(ProjectUser(user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType))))
                project.projectUsers.add(projectUserRepository.save(ProjectUser(user = assignee, project = project, role = Role(id = RoleType.MEMBER.roleType))))

                val category = issueLabelCategoryRepository.save(IssueLabelCategory(name = "타입2", project = project))
                val label = issueLabelRepository.save(IssueLabel(category = category, project = project, name = "기능개선", color = "00ff00"))

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "PR UI 테스트2", body = "본문",
                        toProject = project, fromProject = project,
                        toBranch = "master", fromBranch = "feature2",
                        contributor = manager, state = State.OPEN, number = 2L,
                        assignee = Assignee(user = assignee, project = project),
                        labels = mutableSetOf(label)
                    )
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/${pr.number}").with(authOf(manager))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                val doc = Jsoup.parse(body)
                doc.select("#pr-assignee-select option[value='${assignee.id}']").hasAttr("selected") shouldBe true
                doc.select("#labelIds option[value='${label.id}']").hasAttr("selected") shouldBe true
            }

            it("쓰기 권한이 없는 비로그인 방문자에게는 편집 UI 없이 담당자/라벨 이름만 읽기전용으로 보여야 한다") {
                val suffix = System.currentTimeMillis().toString() + "-3"
                val manager = userRepository.save(User(loginId = "pra-manager3-$suffix", name = "PR매니저3", email = "pra-manager3-$suffix@yona.io"))
                val assignee = userRepository.save(User(loginId = "pra-assignee3-$suffix", name = "담당자3", email = "pra-assignee3-$suffix@yona.io"))
                val project = projectRepository.save(Project(name = "pra-repo3-$suffix", owner = "pra-owner3-$suffix", projectScope = ProjectScope.PUBLIC))
                projectUserRepository.save(ProjectUser(user = manager, project = project, role = Role(id = RoleType.MANAGER.roleType)))

                val category = issueLabelCategoryRepository.save(IssueLabelCategory(name = "타입3", project = project))
                val label = issueLabelRepository.save(IssueLabel(category = category, project = project, name = "문서", color = "0000ff"))

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "PR UI 테스트3", body = "본문",
                        toProject = project, fromProject = project,
                        toBranch = "master", fromBranch = "feature3",
                        contributor = manager, state = State.OPEN, number = 3L,
                        assignee = Assignee(user = assignee, project = project),
                        labels = mutableSetOf(label)
                    )
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/${pr.number}")
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldNotContain "id=\"pr-assignee-select\""
                body shouldContain "담당자3"
                body shouldContain "문서"
            }
        }
    }
}
