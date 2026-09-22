package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import tools.jackson.databind.json.JsonMapper

// password/passwordSalt/token(레거시 전권 토큰)이 어떤 경로로 직렬화되든
// (raw entity 반환, Map<String,Any>에 담긴 경우, 향후 새 컨트롤러 등) 새어나가지 않도록
// User 엔티티 자체에 @JsonIgnore를 걸어 최종 방어선을 둔다. ArchUnit 테스트(컨트롤러
// 시그니처가 raw entity를 직접 반환하지 못하게 강제)와는 서로 다른 구멍을 막는 상호보완 장치다.
class UserJsonSerializationSpec : DescribeSpec({
    val objectMapper = JsonMapper.builder().build()

    describe("User Jackson 직렬화") {
        it("password/passwordSalt/전권 토큰(token)이 JSON에 노출되지 않아야 한다") {
            val user = User(
                id = 1L,
                name = "테스터",
                loginId = "tester",
                password = "argon2id\$v=19\$m=65536,t=3,p=4\$실제해시값",
                passwordSalt = "실제솔트값",
                email = "tester@example.com",
                token = "레거시전권토큰값"
            )

            val json = objectMapper.writeValueAsString(user)

            json shouldNotContain "argon2id"
            json shouldNotContain "실제솔트값"
            json shouldNotContain "레거시전권토큰값"
            json shouldContain "tester"
        }
    }
})
