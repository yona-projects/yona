package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.net.URI

// SvnControllerSpec은 standaloneSetup()(MockMvc가 자체 구성한 DispatcherServlet)이라
// OPTIONS 라우팅이 dispatchOptionsRequest 기본값에 좌우되는 테스트 인프라 특성이 섞였다 — 이
// 스펙은 실제 Spring Boot가 자동설정한 DispatcherServlet(운영과 동일한 dispatchOptionsRequest=true
// 기본값)으로 진짜 svn 클라이언트가 보내는 첫 요청(OPTIONS)이 DAVServlet까지 실제로 도달하는지
// 확정적으로 검증한다 — 만약 도달하지 못하면 실제 svn checkout 자체가 동작하지 않는 심각한 결함이다.
@Transactional
class SvnControllerOptionsIntegrationSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val repositoryService: RepositoryService,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        }

        describe("실제 Spring Boot DispatcherServlet에서의 SVN OPTIONS 요청") {
            it("PUBLIC SVN 프로젝트에 OPTIONS를 보내면 DAVServlet의 WebDAV 메서드 목록(Allow 헤더)이 돌아와야 한다") {
                // BootstrapSetupInterceptor는 회원이 0명이면 /svn/** 을 포함한 모든 요청을 /bootstrap-setup으로
                // 리다이렉트한다(초기 관리자 미생성 상태). 이 스펙을 단독 실행하면 DB에 다른 스펙이 만든
                // 회원이 전혀 없어 실제로 이 가드에 걸려버리므로, 다른 통합 스펙들과 동일하게 회원을 하나
                // 만들어둬 정상적인(이미 부트스트랩된) 운영 상태를 재현한다.
                if (userRepository.count() == 0L) {
                    userRepository.save(User(loginId = "svn-options-admin", name = "관리자", email = "svn-options-admin@yona.io"))
                }

                val project = projectRepository.findAll().find { it.name == "svn-options-test-proj" }
                    ?: projectRepository.save(
                        Project(
                            name = "svn-options-test-proj", owner = "svn-options-owner",
                            vcs = "SUBVERSION", projectScope = ProjectScope.PUBLIC
                        )
                    )
                val repository = repositoryService.getRepository(project)
                // @Transactional은 DB 행만 롤백하고 물리 SVN 저장소 디렉토리는 그대로 남으므로(다른
                // 테스트 실행 시각에 이미 생성돼 있을 수 있음), 없을 때만 새로 만든다(멱등).
                if (!repository.getDirectory().exists()) {
                    repository.create()
                }

                val result = mockMvc.perform(
                    MockMvcRequestBuilders.request(
                        HttpMethod.OPTIONS,
                        URI.create("/svn/${project.owner}/${project.name}")
                    )
                ).andReturn()

                val allow = result.response.getHeader("Allow") ?: ""
                // Spring MVC가 매핑 미스매치 시 자체적으로 채우는 기본 메서드 목록(GET,HEAD,POST,PUT,
                // PATCH,DELETE,OPTIONS)이 아니라, DAVServlet이 실제로 지원하는 WebDAV 전용 메서드가
                // 있어야 진짜 DAVServlet까지 도달했다는 뜻이다 — svn 클라이언트가 이 응답으로 서버가
                // WebDAV/DeltaV를 지원하는지 판단하므로, 이게 틀리면 실제 checkout 자체가 실패한다.
                allow.contains("PROPFIND") shouldBe true
            }
        }
    }
}
