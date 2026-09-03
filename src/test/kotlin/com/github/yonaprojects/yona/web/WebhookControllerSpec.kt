package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.WebhookType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.webhook.Webhook
import com.github.yonaprojects.yona.domain.webhook.WebhookService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import io.mockk.clearMocks

// yona ProjectApp.java:1268,1283,1313 @IsAllowed(Operation.UPDATE)(resourceType 기본값 PROJECT)
// 대응(P1-87) — 세 엔드포인트 전부 매니저 또는 조직관리자만 허용됨을 검증한다.
class WebhookControllerSpec : DescribeSpec({
    val webhookService = mockk<WebhookService>()
    val projectRepository = mockk<ProjectRepository>()
    val userRepository = mockk<UserRepository>()
    val accessControl = mockk<AccessControl>()

    val webhookController = WebhookController(webhookService, projectRepository, userRepository, accessControl)
    val mockMvc = MockMvcBuilders.standaloneSetup(webhookController).build()

    beforeTest {
        clearMocks(webhookService, projectRepository, userRepository, accessControl)
    }

    describe("WebhookController API 단위 테스트") {
        val userAuth = UsernamePasswordAuthenticationToken("owner", "password")
        val project = Project(id = 1L, owner = "owner", name = "test-project")
        val managerUser = User(id = 100L, loginId = "owner", name = "owner")
        val webhook = Webhook(
            id = 10L,
            project = project,
            payloadUrl = "http://localhost:8080/hook",
            secret = "secret",
            gitPush = false,
            webhookType = WebhookType.SIMPLE
        )

        beforeTest {
            every { userRepository.findByLoginId("owner") } returns Optional.of(managerUser)
            every { accessControl.isAllowed(managerUser, project, Operation.UPDATE) } returns true
        }

        describe("GET /projects/{owner}/{projectName}/webhooks") {
            it("웹훅 설정 페이지 뷰를 정상 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { webhookService.findByProject(1L) } returns listOf(webhook)

                mockMvc.perform(
                    get("/projects/owner/test-project/webhooks")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/setting_webhook"))
                    .andExpect(model().attributeExists("webhooks"))
                    .andExpect(model().attributeExists("project"))
            }

            it("존재하지 않는 프로젝트면 404를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

                mockMvc.perform(
                    get("/projects/owner/nosuch/webhooks").principal(userAuth)
                ).andExpect(status().isNotFound)
            }

            // project.id가 없으면(project.id ?: 0L) 0L로 조회해야 한다.
            it("프로젝트 id가 없으면 0L로 웹훅을 조회한다") {
                val noIdProject = Project(id = null, owner = "owner", name = "no-id-project")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "no-id-project") } returns Optional.of(noIdProject)
                every { accessControl.isAllowed(managerUser, noIdProject, Operation.UPDATE) } returns true
                every { webhookService.findByProject(0L) } returns emptyList()

                mockMvc.perform(
                    get("/projects/owner/no-id-project/webhooks").principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 1) { webhookService.findByProject(0L) }
            }
        }

        describe("POST /projects/{owner}/{projectName}/webhooks") {
            it("유효한 웹훅 폼 데이터를 받아 웹훅을 등록하고 웹훅 목록으로 리다이렉트한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every {
                    webhookService.createWebhook(
                        project,
                        "http://localhost:8080/hook",
                        "secret",
                        false,
                        WebhookType.SIMPLE
                    )
                } returns webhook

                mockMvc.perform(
                    post("/projects/owner/test-project/webhooks")
                        .param("payloadUrl", "http://localhost:8080/hook")
                        .param("secret", "secret")
                        .param("gitPush", "false")
                        .param("webhookType", "SIMPLE")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/projects/owner/test-project/webhooks"))

                verify(exactly = 1) {
                    webhookService.createWebhook(
                        project,
                        "http://localhost:8080/hook",
                        "secret",
                        false,
                        WebhookType.SIMPLE
                    )
                }
            }

            // yona Webhook.java:74-81 @Required/@Size(payloadUrl<=2000, secret<=250) 대응 (P2-28). [GL-models_Webhook-006]
            // yona는 Play 폼 바인딩 단계에서 이 검증을 통과 못하면 DB에 닿기도 전에 400을 반환하는데,
            // yona는 이 사전 검증이 없어 그대로 DB에 넣으려다 컬럼 길이 제약 위반(500)이 노출될 수 있었다.
            it("payloadUrl이 비어있으면 400 Bad Request를 반환하고 저장을 시도하지 않는다 (P2-28)") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)

                mockMvc.perform(
                    post("/projects/owner/test-project/webhooks")
                        .param("payloadUrl", "")
                        .param("secret", "secret")
                        .param("gitPush", "false")
                        .param("webhookType", "SIMPLE")
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)

                verify(exactly = 0) { webhookService.createWebhook(any(), any(), any(), any(), any()) }
            }

            it("payloadUrl이 2000자를 넘으면 400 Bad Request를 반환하고 저장을 시도하지 않는다 (P2-28)") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                val tooLongUrl = "http://localhost:8080/" + "a".repeat(2000)

                mockMvc.perform(
                    post("/projects/owner/test-project/webhooks")
                        .param("payloadUrl", tooLongUrl)
                        .param("secret", "secret")
                        .param("gitPush", "false")
                        .param("webhookType", "SIMPLE")
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)

                verify(exactly = 0) { webhookService.createWebhook(any(), any(), any(), any(), any()) }
            }

            it("secret이 250자를 넘으면 400 Bad Request를 반환하고 저장을 시도하지 않는다 (P2-28)") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                val tooLongSecret = "s".repeat(251)

                mockMvc.perform(
                    post("/projects/owner/test-project/webhooks")
                        .param("payloadUrl", "http://localhost:8080/hook")
                        .param("secret", tooLongSecret)
                        .param("gitPush", "false")
                        .param("webhookType", "SIMPLE")
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)

                verify(exactly = 0) { webhookService.createWebhook(any(), any(), any(), any(), any()) }
            }

            // secret을 아예 생략하면(null) `!secret.isNullOrEmpty()`가 false가 되어 길이 검증
            // 자체를 건너뛰는 분기를 태운다 — 기존 성공 테스트는 secret이 항상 비어있지 않았다.
            it("secret을 생략해도(null) 정상적으로 웹훅을 등록한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every {
                    webhookService.createWebhook(project, "http://localhost:8080/hook", null, false, WebhookType.SIMPLE)
                } returns webhook

                mockMvc.perform(
                    post("/projects/owner/test-project/webhooks")
                        .param("payloadUrl", "http://localhost:8080/hook")
                        .param("gitPush", "false")
                        .param("webhookType", "SIMPLE")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)

                verify(exactly = 1) {
                    webhookService.createWebhook(project, "http://localhost:8080/hook", null, false, WebhookType.SIMPLE)
                }
            }

            // secret이 null이 아니라 명시적 빈 문자열이면 !secret.isNullOrEmpty()의 isEmpty() 쪽
            // 하위 분기(null과는 다른 코드 경로)를 태운다 — 위쪽 "생략" 테스트는 null만 사용했다.
            it("secret이 빈 문자열이면(null 아님) 길이 검증을 건너뛰고 정상 등록한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every {
                    webhookService.createWebhook(project, "http://localhost:8080/hook", "", false, WebhookType.SIMPLE)
                } returns webhook

                mockMvc.perform(
                    post("/projects/owner/test-project/webhooks")
                        .param("payloadUrl", "http://localhost:8080/hook")
                        .param("secret", "")
                        .param("gitPush", "false")
                        .param("webhookType", "SIMPLE")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)

                verify(exactly = 1) {
                    webhookService.createWebhook(project, "http://localhost:8080/hook", "", false, WebhookType.SIMPLE)
                }
            }

            // webhookTypeStr이 WebhookType enum에 없는 값이면 catch로 떨어져 기본값 SIMPLE로
            // 대체되는 분기를 태운다 — 기존 테스트는 항상 유효한 "SIMPLE" 값만 사용했다.
            it("webhookType이 잘못된 값이면 기본값 SIMPLE로 대체해 등록한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every {
                    webhookService.createWebhook(project, "http://localhost:8080/hook", "secret", false, WebhookType.SIMPLE)
                } returns webhook

                mockMvc.perform(
                    post("/projects/owner/test-project/webhooks")
                        .param("payloadUrl", "http://localhost:8080/hook")
                        .param("secret", "secret")
                        .param("gitPush", "false")
                        .param("webhookType", "NOT_A_REAL_TYPE")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)

                verify(exactly = 1) {
                    webhookService.createWebhook(project, "http://localhost:8080/hook", "secret", false, WebhookType.SIMPLE)
                }
            }

            it("존재하지 않는 프로젝트면 404를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

                mockMvc.perform(
                    post("/projects/owner/nosuch/webhooks")
                        .param("payloadUrl", "http://localhost:8080/hook")
                        .param("webhookType", "SIMPLE")
                        .principal(userAuth)
                ).andExpect(status().isNotFound)
            }
        }

        describe("DELETE /projects/{owner}/{projectName}/webhooks/{id}") {
            it("웹훅 삭제 비즈니스를 호출하고 200 OK를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { webhookService.deleteWebhook(10L) } returns Unit

                mockMvc.perform(
                    delete("/projects/owner/test-project/webhooks/10")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 1) { webhookService.deleteWebhook(10L) }
            }

            it("존재하지 않는 프로젝트면 404를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

                mockMvc.perform(
                    delete("/projects/owner/nosuch/webhooks/10").principal(userAuth)
                ).andExpect(status().isNotFound)
            }
        }

        describe("권한 검사 (P1-87)") {
            it("비로그인 사용자는 웹훅 목록 조회가 403으로 거부된다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { accessControl.isAllowed(null, project, Operation.UPDATE) } returns false

                mockMvc.perform(get("/projects/owner/test-project/webhooks"))
                    .andExpect(status().isForbidden)
            }

            it("프로젝트 매니저가 아닌 로그인 사용자는 웹훅 생성이 403으로 거부된다") {
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                val stranger = User(id = 200L, loginId = "stranger", name = "stranger")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { accessControl.isAllowed(stranger, project, Operation.UPDATE) } returns false

                mockMvc.perform(
                    post("/projects/owner/test-project/webhooks")
                        .param("payloadUrl", "http://localhost:8080/hook")
                        .param("webhookType", "SIMPLE")
                        .principal(strangerAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { webhookService.createWebhook(any(), any(), any(), any(), any()) }
            }

            it("프로젝트 매니저가 아닌 로그인 사용자는 웹훅 삭제가 403으로 거부된다") {
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                val stranger = User(id = 200L, loginId = "stranger", name = "stranger")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { accessControl.isAllowed(stranger, project, Operation.UPDATE) } returns false

                mockMvc.perform(
                    delete("/projects/owner/test-project/webhooks/10")
                        .principal(strangerAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { webhookService.deleteWebhook(any()) }
            }
        }

        // yona-wiki P3-02 Step8.6 항목1(2026-09-01) — `web/WebhookRestApiController.kt`가 위임하는
        // JSON 전용 메서드. 기존 webhooks()와 동일한 프로젝트 조회 + 권한 체크를 재사용하지만
        // Thymeleaf 뷰 대신 JSON 바디를 반환한다.
        describe("listWebhooksJson") {
            it("웹훅 목록을 JSON으로 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { webhookService.findByProject(1L) } returns listOf(webhook)

                val result = webhookController.listWebhooksJson("owner", "test-project", userAuth)

                result.statusCode.value() shouldBe 200
                @Suppress("UNCHECKED_CAST")
                val body = result.body as List<Map<String, Any?>>
                body.size shouldBe 1
                body[0]["id"] shouldBe 10L
                body[0]["payloadUrl"] shouldBe "http://localhost:8080/hook"
            }

            it("존재하지 않는 프로젝트면 404를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

                val result = webhookController.listWebhooksJson("owner", "nosuch", userAuth)

                result.statusCode.value() shouldBe 404
            }

            it("권한이 없으면 403을 반환한다") {
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                val stranger = User(id = 200L, loginId = "stranger", name = "stranger")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { accessControl.isAllowed(stranger, project, Operation.UPDATE) } returns false

                val result = webhookController.listWebhooksJson("owner", "test-project", strangerAuth)

                result.statusCode.value() shouldBe 403
            }
        }
    }
})
