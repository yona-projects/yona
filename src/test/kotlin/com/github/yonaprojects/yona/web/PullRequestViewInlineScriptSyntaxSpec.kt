package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
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

// pullrequest/view.html의 하단 <script th:inline="javascript"> 블록 안에서 confirm()/alert()/
// $.post(...)에 넘기는 문자열들이 "[[#{...}]]" / "[[@{...}]]"처럼 수동으로 따옴표를 감싼 채
// 인라인 표현식을 썼다 - th:inline="javascript"가 String 타입 표현식 결과를 이미 JS 문자열
// 리터럴로 자동 따옴표 처리하기 때문에, 수동 따옴표와 겹쳐 결과 JS 코드 자체가 깨진다
// (issue/view.html에서 이미 한 번 겪은 것과 같은 회귀 클래스 - docs/parity/tickets/p3-49.md).
// 예: "[[#{pullRequest.merge}]]?" -> ""코드 병합"?" (따옴표 뒤에 공백으로 구분된 두 토큰이
// 바로 이어져 SyntaxError). 이 스크립트 블록 하나가 깨지면 watch 토글/담당자·라벨 변경/
// 리뷰 등록/승인 등 이 페이지의 모든 버튼 핸들러가 통째로 죽는다.
@Transactional
class PullRequestViewInlineScriptSyntaxSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val pullRequestRepository: PullRequestRepository
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("PR 상세 화면 하단 인라인 스크립트의 JS 문법이 깨지지 않아야 한다") {
            it("confirm/alert/POST 호출에 쓰인 인라인 메시지·URL 표현식에 따옴표가 중복되면 안 된다") {
                val suffix = System.currentTimeMillis().toString()
                val contributor = userRepository.save(User(loginId = "prsyn-author-$suffix", name = "PR작성자", email = "prsyn-author-$suffix@yona.io"))
                val project = projectRepository.save(Project(name = "prsyn-repo-$suffix", owner = "prsyn-org-$suffix", projectScope = ProjectScope.PUBLIC))
                project.projectUsers.add(projectUserRepository.save(ProjectUser(user = contributor, project = project, role = Role(id = RoleType.MANAGER.roleType))))

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "인라인스크립트 문법 검증 PR", body = "본문",
                        toProject = project, fromProject = project,
                        toBranch = "master", fromBranch = "feature",
                        contributor = contributor, state = State.OPEN, number = 1L
                    )
                )

                val userDetails = YonaUserDetails(
                    id = contributor.id!!,
                    loginId = contributor.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/${pr.number}").with(user(userDetails))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                // confirm()에 쓰인 pullRequest.merge 메시지 - 따옴표가 중복되면 ""코드 병합"?"
                // 처럼 나온다. 정확히 한 번만 감싸져야 한다.
                val confirmCall = body.substringAfter("if (!confirm(").substringBefore(")) { return; }")
                confirmCall.contains("\"\"") shouldBe false

                // review/unreview POST URL - 중복 따옴표 시 $.post(""/api/...", ...) 형태로 깨진다.
                // th:inline="javascript"의 자동 JSON 문자열 직렬화는 슬래시를 \/ 로 이스케이프하는데
                // (유효한 JS 문자열 이스케이프, 브라우저는 런타임에 /로 해석) 이건 버그가 아니다.
                val reviewCall = body.substringAfter("$(\"#btn-review\").click(function() {").substringBefore("});")
                reviewCall.contains("\"\"") shouldBe false
                reviewCall shouldContain "pullRequest\\/${pr.id}\\/review"

                val unreviewCall = body.substringAfter("$(\"#btn-unreview\").click(function() {").substringBefore("});")
                unreviewCall.contains("\"\"") shouldBe false
                unreviewCall shouldContain "pullRequest\\/${pr.id}\\/unreview"
            }
        }
    }
}
