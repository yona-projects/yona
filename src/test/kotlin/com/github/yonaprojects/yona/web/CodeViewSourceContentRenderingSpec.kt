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
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.io.File

// P3-63 회귀 스펙: 코드 브라우저에서 바이너리도 마크다운도 아닌 일반 텍스트/소스 코드 파일을 열면
// 실제 파일 내용은 숨겨진 `#codeVal`(class="hidden")에만 채워지고, 화면에 실제로 보이는
// `#showCode`(<pre>)는 항상 비어있게 렌더링되던 버그(legacy `service/yobi.code.Browser.js`가
// `#codeVal` -> Ace 에디터(`#showCode`)로 내용을 복사했는데, 이 JS가 어떤 yona 템플릿에서도
// 로드되지 않아 연결이 끊겨 있었음 — Ace/AJAX 트리 완전 이식은 별도 범위, 이번엔 "내용이 안 보이는"
// 핵심 버그만 최소 비용으로 고친다).
class CodeViewSourceContentRenderingSpec @Autowired constructor(
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

        describe("code/view.html 일반 텍스트/소스 코드 파일 내용 렌더링 (P3-63)") {
            it("바이너리도 마크다운도 아닌 파일을 열면 실제 내용이 #showCode 안에 보여야 하고, hidden 클래스 뒤에 숨어있으면 안 된다") {
                val owner = userRepository.findByLoginId("p363-owner").orElseGet {
                    userRepository.save(User(loginId = "p363-owner", name = "P363오너", email = "p363-owner@yona.io"))
                }
                val project = projectRepository.findAll().find { it.name == "p363-proj" } ?: projectRepository.save(
                    Project(name = "p363-proj", owner = "p363-owner", projectScope = ProjectScope.PUBLIC, vcs = "GIT")
                )
                val gitDir = File(File(gitBaseDir), "${project.owner}/${project.name}.git")
                // gitBaseDir(기본값 /tmp/yona/git)은 세션을 넘나드는 고정 경로라, 예전 실행이
                // 중단돼 objects/refs만 있고 HEAD가 없는 반쪽짜리 bare 저장소가 남아 있을 수 있다
                // (CodeBrowserListWrapRenderingSpec에서 실측 확인된 패턴 그대로 적용) —
                // gitDir.exists()만 보면 "이미 준비됨"으로 오판해 커밋을 건너뛴다. 실제 초기화 완료
                // 여부(HEAD 파일)로 판단해야 안전하다.
                val marker = "P363_SAMPLE_SOURCE_CONTENT_MARKER"
                val javaContent = "public class Sample {\n    // $marker\n    void run() {}\n}\n"
                if (!File(gitDir, "HEAD").exists()) {
                    repositoryService.getRepository(project).create()
                    BareCommit(project, owner, gitBaseDir).commitTextFile("Sample.java", javaContent, "add Sample.java")
                }

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/code/main/Sample.java"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // 핵심 회귀 검증: 실제 파일 내용이 응답 본문에 존재해야 한다(당연히 통과했었음 — 버그는
                // "존재하지만 hidden에 숨어 화면에 안 보임"이었다).
                body shouldContain marker

                // #showCode(<pre>) 안에 실제 내용이 채워져 있어야 한다 — 버그 상태에서는 이 <pre>가
                // 항상 빈 채로 렌더링되므로 이 부분이 RED로 실패한다.
                val showCodeRegex = Regex("<pre id=\"showCode\"[^>]*>(.*?)</pre>", RegexOption.DOT_MATCHES_ALL)
                val showCodeMatch = showCodeRegex.find(body)
                showCodeMatch shouldNotBe null
                showCodeMatch!!.groupValues[1] shouldContain marker

                // 실제 내용이 class="hidden"이 붙은 요소 뒤에 숨어있으면 안 된다(legacy 마크업 잔재인
                // `<div id="codeVal" class="hidden">...` 패턴 자체가 사라져야 한다).
                body shouldNotContain "class=\"hidden\""
            }
        }
    }
}
