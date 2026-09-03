package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

// yona-wiki P3-02 Step8.6 항목1(2026-09-01, 우선순위 1위) — `yona admin webhook list`용 신규
// JSON REST API. WebhookController.listWebhooksJson()에 위임하는 얇은 어댑터인지만 검증한다.
class WebhookRestApiControllerSpec : DescribeSpec({
    val webhookController = mockk<WebhookController>()

    val controller = WebhookRestApiController(webhookController)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(webhookController)
    }

    describe("GET /api/v1/projects/{owner}/{project}/webhooks") {
        it("WebhookController.listWebhooksJson에 위임한다") {
            every { webhookController.listWebhooksJson("yona", "yona", any()) } returns ResponseEntity.ok(emptyList<Any>())

            mockMvc.perform(get("/api/v1/projects/yona/yona/webhooks"))
                .andExpect(status().isOk)

            verify(exactly = 1) { webhookController.listWebhooksJson("yona", "yona", any()) }
        }
    }
})
