package com.github.yonaprojects.yona.config.oauth2server

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

// yona-wiki P3-14 1라운드 — 발급 가능한 리소스(mcp/api) 레지스트리.
class ProtectedResourceSpec : DescribeSpec({

    describe("uri") {
        it("MCP는 /mcp를 붙인다") {
            ProtectedResource.MCP.uri("http://localhost:8080") shouldBe "http://localhost:8080/mcp"
        }

        it("API는 /api/v1을 붙인다") {
            ProtectedResource.API.uri("http://localhost:8080") shouldBe "http://localhost:8080/api/v1"
        }
    }

    describe("fromUri") {
        it("알려진 리소스 URI는 해당 enum 값을 반환해야 한다") {
            ProtectedResource.fromUri("http://localhost:8080/mcp", "http://localhost:8080") shouldBe ProtectedResource.MCP
            ProtectedResource.fromUri("http://localhost:8080/api/v1", "http://localhost:8080") shouldBe ProtectedResource.API
        }

        it("알 수 없는 리소스 URI는 null을 반환해야 한다") {
            ProtectedResource.fromUri("http://localhost:8080/unknown", "http://localhost:8080").shouldBeNull()
            ProtectedResource.fromUri(null, "http://localhost:8080").shouldBeNull()
        }
    }
})
