package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.shouldBe
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

// pullrequest/clone.html의 <script th:inline="javascript"> 블록도 pullrequest/view.html과 같은
// 클래스의 버그였다 - cloneUrl/cloneParam/실패 alert 문구를 전부 수동으로 따옴표를 감싼 채
// [[...]]로 썼다. th:inline="javascript"가 String 표현식 결과를 이미 JS 문자열 리터럴로
// 자동 직렬화하므로 수동 따옴표와 겹쳐 SyntaxError가 난다 - 이 인터스티셜 화면은 3초 후
// doClone()을 호출해 실제 포크를 완료해야 하는데, 스크립트 블록이 깨지면 그 호출 자체가
// 실행되지 않아 "복제 중입니다" 화면에서 영원히 멈춘다.
@Transactional
class PullRequestCloneInlineScriptSyntaxSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("포크 인터스티셜 화면(pullrequest/clone.html) 인라인 스크립트 문법") {
            it("cloneUrl/cloneParam/실패 alert 표현식에 따옴표가 중복되면 안 된다") {
                val suffix = System.currentTimeMillis().toString()
                val owner = userRepository.save(User(loginId = "clnsyn-owner-$suffix", name = "원본소유자", email = "clnsyn-owner-$suffix@yona.io"))
                val forker = userRepository.save(User(loginId = "clnsyn-forker-$suffix", name = "포커", email = "clnsyn-forker-$suffix@yona.io"))
                val original = projectRepository.save(Project(name = "clnsyn-repo-$suffix", owner = owner.loginId, projectScope = ProjectScope.PUBLIC))

                val forkerDetails = YonaUserDetails(
                    id = forker.id!!,
                    loginId = forker.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                val body = mockMvc.perform(
                    post("/${original.owner}/${original.name}/fork")
                        .param("owner", forker.loginId)
                        .param("name", original.name)
                        .param("projectScope", "PUBLIC")
                        .with(user(forkerDetails))
                        .with(csrf())
                ).andExpect(status().isOk).andReturn().response.contentAsString

                val setTimeoutBlock = body.substringAfter("setTimeout(function(){").substringBefore("}, 3000);")
                setTimeoutBlock.contains("\"\"") shouldBe false
                setTimeoutBlock.contains("doClone") shouldBe true
            }
        }
    }
}
