package com.github.yonaprojects.yona.config.git

import com.github.yonaprojects.yona.domain.deploykey.DeployKey
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyService
import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken

class DeployKeyAuthenticationProviderSpec : DescribeSpec({

    describe("DeployKeyAuthenticationProvider") {
        it("사용자명이 x-access-deploykey가 아니면 null을 반환해(다음 provider로 위임) 일반 로그인에 영향을 주지 않아야 한다") {
            val deployKeyService = mockk<DeployKeyService>()
            val provider = DeployKeyAuthenticationProvider(deployKeyService)

            val result = provider.authenticate(UsernamePasswordAuthenticationToken("chulsoo", "pass123"))

            result shouldBe null
        }

        it("사용자명이 맞아도 알 수 없는 토큰이면 null을 반환해야 한다") {
            val deployKeyService = mockk<DeployKeyService>()
            every { deployKeyService.findByHttpsToken("bad-token") } returns null
            val provider = DeployKeyAuthenticationProvider(deployKeyService)

            val result = provider.authenticate(
                UsernamePasswordAuthenticationToken(DeployKeyAuthenticationProvider.DEPLOY_KEY_USERNAME, "bad-token")
            )

            result shouldBe null
        }

        it("사용자명과 유효한 토큰이 일치하면 인증된 DeployKeyAuthenticationToken을 반환하고 사용 기록을 남겨야 한다") {
            val project = Project(id = 1L, owner = "gildong", name = "repo-a")
            val deployKey = DeployKey(id = 10L, project = project, title = "키", readOnly = true)
            val deployKeyService = mockk<DeployKeyService>()
            every { deployKeyService.findByHttpsToken("good-token") } returns deployKey
            every { deployKeyService.markUsed(deployKey) } returns Unit
            val provider = DeployKeyAuthenticationProvider(deployKeyService)

            val result = provider.authenticate(
                UsernamePasswordAuthenticationToken(DeployKeyAuthenticationProvider.DEPLOY_KEY_USERNAME, "good-token")
            )

            result shouldNotBe null
            (result as DeployKeyAuthenticationToken).deployKey shouldBe deployKey
            result.isAuthenticated shouldBe true
            verify(exactly = 1) { deployKeyService.markUsed(deployKey) }
        }
    }
})
