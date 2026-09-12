package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.BareCommit
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.io.File

// P3-63(후속): 코드 브라우저 파일 뷰에 legacy service/yobi.code.Browser.js와 동치인 진짜 Ace
// 에디터를 연결한다. 직전 커밋(CodeViewSourceContentRenderingSpec)에서는 hljs 폴백으로 "내용이
// 안 보이는" 핵심 버그만 최소 비용으로 고쳤고, Ace 자체는 아직 로드/연결되지 않았었다. 이번 스펙은
// 그 gap을 검증한다: ace.js 스크립트가 이 템플릿에 로드되는지, ace.edit("showCode")로 실제
// 에디터를 붙이는 인라인 스크립트가 있는지, 확장자 기반 모드 매핑 테이블이 이식돼 있는지(.java ->
// "java"), 서버가 파일 경로를 클라이언트에 넘겨주는지(data-path), 그리고 ace가 없을 때 hljs
// 폴백이 여전히 살아있는지를 확인한다.
class CodeViewAceEditorRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val repositoryService: RepositoryService,
    @Value("\${yona.git.base-dir:/tmp/yona/git}") private val gitBaseDir: String
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
        }

        describe("code/view.html 파일 내용 뷰의 Ace 에디터 연결 (P3-63 후속)") {
            it("일반 소스 코드(.java) 파일을 열면 ace.js 스크립트 로드 + ace.edit(\"showCode\") 연결 + java 모드 매핑 + data-path가 응답 본문에 있어야 한다") {
                val owner = userRepository.findByLoginId("p363ace-owner").orElseGet {
                    userRepository.save(User(loginId = "p363ace-owner", name = "P363Ace오너", email = "p363ace-owner@yona.io"))
                }
                val project = projectRepository.findAll().find { it.name == "p363ace-proj" } ?: projectRepository.save(
                    Project(name = "p363ace-proj", owner = "p363ace-owner", projectScope = ProjectScope.PUBLIC, vcs = "GIT")
                )
                val gitDir = File(File(gitBaseDir), "${project.owner}/${project.name}.git")
                val marker = "P363ACE_SAMPLE_SOURCE_CONTENT_MARKER"
                val javaContent = "public class Sample {\n    // $marker\n    void run() {}\n}\n"
                if (!File(gitDir, "HEAD").exists()) {
                    repositoryService.getRepository(project).create()
                    BareCommit(project, owner, gitBaseDir).commitTextFile("Sample.java", javaContent, "add Sample.java")
                }

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/code/main/Sample.java"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // 1) Ace 본체 스크립트가 이 템플릿에서 로드돼야 한다(전역 layout에는 없음 — 이 화면 전용).
                body shouldContain "<script src=\"/javascripts/lib/ace/ace.js\">"

                // 2) 실제로 #showCode에 Ace 에디터를 붙이는 호출이 인라인 스크립트에 있어야 한다.
                body shouldContain "ace.edit(\"showCode\")"

                // 3) 확장자 -> Ace 모드 매핑 테이블이 이식돼 있어야 한다(legacy ext2mode의 java 항목).
                body shouldContain "\"java\": [\"java\"]"

                // 4) 서버가 알고 있는 파일 경로(확장자 계산의 근거)를 클라이언트에 넘겨줘야 한다.
                body shouldContain "data-path=\"Sample.java\""

                // 5) Ace가 없는 경우를 위한 hljs 폴백이 여전히 남아있어야 한다(완전 대체 금지).
                body shouldContain "typeof hljs !== \"undefined\""
            }
        }
    }
}
