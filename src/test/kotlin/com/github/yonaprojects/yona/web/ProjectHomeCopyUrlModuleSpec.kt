package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// 사용자가 실제 브라우저에서 재현: 프로젝트 홈(/{owner}/{projectName})의 "주소복사"(Copy URL) 버튼이
// 눌러도 아무 반응이 없음.
//
// 원인: legacy project/home.scala.html:165-177는 페이지 하단에서 항상
// `$yobi.loadModule("project.Home", {...})`을 호출해 clone-URL 복사(ClipboardJS)/설명 수정 저장/
// 라벨 편집 등을 배선하는데, yona project/home.html에는 이 호출 자체가 통째로 없다(grep 0건) —
// 버튼의 HTML(`data-clipboard-target`, `id="cloneURLBtn"`)과 그 대상으로 쓰이는 클라이언트 JS
// 모듈(`yobi.project.Home.js`)은 그대로 다 있는데 아무도 그 모듈을 초기화하지 않아 죽은 버튼이었다.
// (`ClipboardJS` 자체는 사이트 전역 `yona-common.js`에 이미 번들돼 있어 별도 <script> 추가는 불필요
// — 실제로 legacy도 clipboard.js를 이 페이지에 별도로 로드하지 않는다, 직접 대조 확인.)
class ProjectHomeCopyUrlModuleSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    private val mockMvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(wac).build() }

    init {
        describe("project/home.html의 project.Home 모듈 배선 (주소복사 버튼)") {
            it("페이지 하단에 \$yobi.loadModule(\"project.Home\", ...)이 project.id/description 저장 URL과 함께 실제로 렌더링돼야 한다") {
                // BootstrapSetupInterceptor는 DB에 유저가 0명이면 무조건 /bootstrap-setup으로
                // 리다이렉트하므로, 인증 없이 GET하는 이 화면도 유저를 최소 1명 만들어둬야 한다.
                userRepository.findByLoginId("phc-bootstrap1").orElseGet {
                    userRepository.save(User(loginId = "phc-bootstrap1", name = "부트스트랩", email = "phc-bootstrap1@yona.io"))
                }
                val project = projectRepository.findAll().find { it.name == "phc-proj" } ?: projectRepository.save(
                    Project(name = "phc-proj", owner = "phc-owner", projectScope = ProjectScope.PUBLIC, vcs = "GIT", isCodeEnabled = true)
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "loadModule(\"project.Home\""
                body shouldContain "\"nProjectId\": ${project.id}"
                // th:inline="javascript"가 URL 문자열의 '/'를 JS 문자열 리터럴 이스케이프(\/)로
                // 렌더링한다(런타임엔 동일한 문자열로 평가되는 정상 동작) — 렌더링된 그대로 확인한다.
                body shouldContain "\\/api\\/projects\\/${project.id}"
            }
        }
    }
}
