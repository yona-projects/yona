package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// P3-58: 조직 범위 검색 결과 화면(search/list.html)이 조직 헤더/메뉴(organization/header,
// organization/menu) fragment 대신 즉석 .org-header 블록을 쓰고 있어 조직 탐색 메뉴(홈/이슈/
// 게시판/PR 탭)와 로고/브레드크럼/가입요청 드롭다운이 전부 사라져 있었다. 다른 조직 화면
// (error/forbidden_organization.html)과 동일한 표준 fragment로 교체했는지 검증한다.
class OrganizationSearchHeaderTemplateRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val organizationRepository: OrganizationRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("P3-58: 조직 범위 검색 결과 화면에 표준 organization/header, organization/menu fragment가 렌더링돼야 한다") {
            // BootstrapSetupInterceptor가 DB에 회원이 0명이면 모든 요청을 /bootstrap-setup으로
            // 리다이렉트한다 - 최소 1명은 있어야 정상 라우팅된다.
            userRepository.findByLoginId("orgsearch-seed-user").orElseGet {
                userRepository.save(User(loginId = "orgsearch-seed-user", name = "시드유저", email = "orgsearch-seed-user@yona.io"))
            }

            val org = organizationRepository.findByName("orgsearch-test-org").orElseGet {
                organizationRepository.save(Organization(name = "orgsearch-test-org", descr = "테스트 조직"))
            }

            it("즉석 .org-header 블록이 사라지고 organization/header 표준 fragment(로고 이미지)가 렌더링돼야 한다") {
                val html = mockMvc.perform(get("/org/${org.name}/search").param("keyword", "test"))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select(".org-header").size shouldBe 0
                doc.select(".project-header-outer img[src='/organizations/${org.id}/logo']").size shouldBe 1
            }

            it("organization/menu 표준 fragment(조직 홈/이슈/게시판/PR 탭 내비게이션)가 렌더링돼야 한다") {
                val html = mockMvc.perform(get("/org/${org.name}/search").param("keyword", "test"))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select(".project-menu-outer a[href='/organizations/${org.name}']").size shouldBe 1
                doc.select(".project-menu-outer a[href='/org/${org.name}/issues']").size shouldBe 1
                doc.select(".project-menu-outer a[href='/org/${org.name}/boards']").size shouldBe 1
                doc.select(".project-menu-outer a[href='/org/${org.name}/pullrequests']").size shouldBe 1
            }

            it("검색 화면은 특정 조직 메뉴 탭에 속하지 않으므로 활성(active) 탭이 없어야 한다") {
                val html = mockMvc.perform(get("/org/${org.name}/search").param("keyword", "test"))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select(".project-menu-outer li.active").size shouldBe 0
            }
        }
    }
}
