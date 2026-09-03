package com.github.yonaprojects.yona.domain.webhook

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

// WebhookRepository.existsByHash()는 Ebean 시절 Etag 호환용으로 남겨둔 인터페이스 default
// 메서드로, 입력과 무관하게 항상 false를 반환한다(분기 없음) — mockk의 callOriginal()로 실제
// default 구현을 그대로 호출해 라인 커버리지를 채운다.
class WebhookRepositorySpec : DescribeSpec({
    describe("existsByHash()") {
        it("항상 false를 반환해야 한다(Ebean 호환용 default 구현)") {
            val repository = mockk<WebhookRepository>()
            every { repository.existsByHash(any()) } answers { callOriginal() }

            repository.existsByHash("any-hash") shouldBe false
            repository.existsByHash("") shouldBe false
        }
    }
})
