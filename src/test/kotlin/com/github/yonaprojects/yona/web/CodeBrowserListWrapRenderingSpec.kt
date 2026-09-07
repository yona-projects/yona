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

// 사용자가 실제 브라우저에서 재현: 프로젝트에 README.md를 커밋한 뒤 코드 브라우저(/code/{branch})에
// 들어가도 파일 목록이 화면에 전혀 보이지 않던 문제(P0-27) 회귀 검증.
//
// 원인: code/view.html은 `<head th:replace="~{site/layout :: head(...)}"> <style>...</style> </head>`
// 형태로, 페이지 전용 <style>(.list-wrap 등)을 th:replace가 통째로 대체하는 <head> 태그 "안"에 넣어뒀다.
// Thymeleaf th:replace는 호스트 태그(<head>)와 그 자식(내부의 <style>)을 전부 프래그먼트 결과로
// 치환하므로, 이 <style> 블록은 실제로는 단 한 번도 렌더링되지 않는 죽은 코드였다(이 저장소의 다른
// 모든 템플릿은 `<head th:replace="..."></head>`처럼 안을 비워두는 게 정상 패턴 — grep으로 확인).
// 그 결과 공용 stylesheets/yobi.css의 `.code-browse-wrap .list-wrap { display: none; }`(legacy가
// JS `$yobi.loadModule("code.Browser", ...)`의 `$(".list-wrap").show()`로 여는 것을 전제로 한 규칙,
// yona는 이 JS 모듈을 포팅하지 않음)만 유일하게 적용되는 display 선언이 되어 폴더 목록 전체가
// `display:none`으로 항상 숨겨져 있었다.
class CodeBrowserListWrapRenderingSpec @Autowired constructor(
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

        describe("code/view.html 폴더 목록(.list-wrap) 실제 렌더링 (P0-27)") {
            it("페이지 전용 CSS가 <head th:replace>에 먹히지 않고 실제 응답 본문에 렌더링돼야 한다") {
                val owner = userRepository.findByLoginId("cbw-owner").orElseGet {
                    userRepository.save(User(loginId = "cbw-owner", name = "코드브라우저오너", email = "cbw-owner@yona.io"))
                }
                val project = projectRepository.findAll().find { it.name == "cbw-proj" } ?: projectRepository.save(
                    Project(name = "cbw-proj", owner = "cbw-owner", projectScope = ProjectScope.PUBLIC, vcs = "GIT")
                )
                val gitDir = File(File(gitBaseDir), "${project.owner}/${project.name}.git")
                // gitBaseDir(기본값 /tmp/yona/git)은 세션을 넘나드는 고정 경로라, 예전 실행이
                // 중단돼 objects/refs만 있고 HEAD가 없는 반쪽짜리 bare 저장소가 남아 있을 수 있다
                // (실측 확인, 2026-09-07) — gitDir.exists()만 보면 "이미 준비됨"으로 오판해 커밋을
                // 건너뛴다. 실제 초기화 완료 여부(HEAD 파일)로 판단해야 안전하다.
                if (!File(gitDir, "HEAD").exists()) {
                    repositoryService.getRepository(project).create()
                    BareCommit(project, owner, gitBaseDir).commitTextFile("README.md", "# cbw-proj", "테스트")
                }

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/code/main"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // 폴더 목록 마크업 자체는 원래도 응답 본문에 있었다(버그가 아니었음) — 문제는 이걸
                // 보이게 만드는 CSS가 렌더링되지 않던 것이므로, 목록 마크업이 아니라 그 CSS 규칙이
                // 실제로 응답에 포함되는지를 검증해야 이 버그를 정확히 잡아낸다.
                body shouldContain "README.md"
                body shouldContain ".list-wrap {"
            }

            // P0-27 수정으로 페이지 전용 <style>이 실제로 렌더링되기 시작하면서 새로 드러난 문제
            // (사용자가 실제 화면에서 재현): `.code-browse-header`(브랜치 선택+브레드크럼+"새 파일"/
            // "ZIP 다운로드" 버튼 행)를 스코프 없이 선언해뒀는데, 공용 yobi.css에는 더 구체적인
            // `.code-browse-wrap .code-browse-header { margin-top:10px; margin-bottom:10px;
            // display:block; height:34px; }`가 이미 있다(legacy 원본용, select2 위젯 기준 고정
            // height). CSS 명시도(specificity)는 항상 선택자 구체성이 우선이라 소스 순서와 무관하게
            // 이 legacy 규칙이 이겨서, 로컬이 의도한 `display:flex`(버튼을 justify-content:
            // space-between으로 배치) 대신 `display:block`+고정 34px 높이가 강제되고 margin-bottom도
            // 15px가 아닌 10px로 깎인다 — 그 결과 float된 pull-right 버튼들이 고정 높이 박스를
            // 벗어나며 여백 없이 목록과 붙어버린다(레거시에는 없는 yona 전용 회귀).
            it("`.code-browse-header` 로컬 스타일이 legacy yobi.css의 더 구체적인 선택자에 밀리지 않도록 스코프돼야 한다") {
                val owner = userRepository.findByLoginId("cbw-owner").orElseGet {
                    userRepository.save(User(loginId = "cbw-owner", name = "코드브라우저오너", email = "cbw-owner@yona.io"))
                }
                val project = projectRepository.findAll().find { it.name == "cbw-proj" } ?: projectRepository.save(
                    Project(name = "cbw-proj", owner = "cbw-owner", projectScope = ProjectScope.PUBLIC, vcs = "GIT")
                )
                val gitDir = File(File(gitBaseDir), "${project.owner}/${project.name}.git")
                // gitBaseDir(기본값 /tmp/yona/git)은 세션을 넘나드는 고정 경로라, 예전 실행이
                // 중단돼 objects/refs만 있고 HEAD가 없는 반쪽짜리 bare 저장소가 남아 있을 수 있다
                // (실측 확인, 2026-09-07) — gitDir.exists()만 보면 "이미 준비됨"으로 오판해 커밋을
                // 건너뛴다. 실제 초기화 완료 여부(HEAD 파일)로 판단해야 안전하다.
                if (!File(gitDir, "HEAD").exists()) {
                    repositoryService.getRepository(project).create()
                    BareCommit(project, owner, gitBaseDir).commitTextFile("README.md", "# cbw-proj", "테스트")
                }

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/code/main"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain ".code-browse-wrap .code-browse-header {"
            }

            // 위 두 건을 고치며 전수 조사한 결과, 같은 유형의 명시도 충돌이 훨씬 더 넓게 남아있었음을
            // 발견(사용자가 "이런 식으로 하나씩 찾는 건 한계가 있다"며 전체 점검을 요청해 진행한
            // 감사에서 발견) — `.listhead`/`.listitem`/`.filename`/`.commitMsg`/`.commitDate`/
            // `.file-wrap`/`.file-header`/`.file-info`/`.code-wrap`가 전부 스코프 없이 선언돼 있어
            // 공용 yobi.css의 `.code-browse-wrap .listhead`/`.code-browse-wrap .listitem .commitDate`
            // 등 더 구체적인 규칙에 밀린다(파일 목록 정렬/줄바꿈, 단일 파일 뷰 헤더 레이아웃이 전부
            // 영향받음). 나머지도 전부 `.code-browse-wrap` 프리픽스로 스코프해 일괄 해결.
            it("파일 목록/단일 파일 뷰의 나머지 로컬 셀렉터도 전부 legacy yobi.css보다 명시도가 밀리지 않게 스코프돼야 한다") {
                val owner = userRepository.findByLoginId("cbw-owner").orElseGet {
                    userRepository.save(User(loginId = "cbw-owner", name = "코드브라우저오너", email = "cbw-owner@yona.io"))
                }
                val project = projectRepository.findAll().find { it.name == "cbw-proj" } ?: projectRepository.save(
                    Project(name = "cbw-proj", owner = "cbw-owner", projectScope = ProjectScope.PUBLIC, vcs = "GIT")
                )
                val gitDir = File(File(gitBaseDir), "${project.owner}/${project.name}.git")
                // gitBaseDir(기본값 /tmp/yona/git)은 세션을 넘나드는 고정 경로라, 예전 실행이
                // 중단돼 objects/refs만 있고 HEAD가 없는 반쪽짜리 bare 저장소가 남아 있을 수 있다
                // (실측 확인, 2026-09-07) — gitDir.exists()만 보면 "이미 준비됨"으로 오판해 커밋을
                // 건너뛴다. 실제 초기화 완료 여부(HEAD 파일)로 판단해야 안전하다.
                if (!File(gitDir, "HEAD").exists()) {
                    repositoryService.getRepository(project).create()
                    BareCommit(project, owner, gitBaseDir).commitTextFile("README.md", "# cbw-proj", "테스트")
                }

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/code/main"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain ".code-browse-wrap .listhead {"
                body shouldContain ".code-browse-wrap .listitem {"
                body shouldContain ".code-browse-wrap .filename {"
                body shouldContain ".code-browse-wrap .commitMsg {"
                body shouldContain ".code-browse-wrap .commitDate {"
                body shouldContain ".code-browse-wrap .file-wrap {"
                body shouldContain ".code-browse-wrap .file-header {"
                body shouldContain ".code-browse-wrap .file-info {"
                body shouldContain ".code-browse-wrap .code-wrap {"
            }
        }
    }
}
