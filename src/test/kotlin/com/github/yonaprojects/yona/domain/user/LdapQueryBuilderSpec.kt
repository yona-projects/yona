package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import javax.naming.directory.Attribute
import javax.naming.directory.Attributes
import javax.naming.directory.BasicAttribute
import javax.naming.directory.BasicAttributes
import javax.naming.NamingException

class LdapQueryBuilderSpec : DescribeSpec({
    describe("LdapQueryBuilder.guessUser") {
        it("이메일 기반 로그인을 쓰지 않으면 입력값을 그대로 반환해야 한다") {
            LdapQueryBuilder.guessUser("gildong", useEmailBaseLogin = false) { "should-not-be-called@example.com" } shouldBe "gildong"
        }

        it("이메일 기반 로그인을 쓰면 로컬 유저의 이메일로 치환해야 한다") {
            LdapQueryBuilder.guessUser("gildong", useEmailBaseLogin = true) { "gildong@example.com" } shouldBe "gildong@example.com"
        }

        it("이메일 기반 로그인이지만 로컬에 매칭되는 유저가 없으면 원본 입력을 그대로 반환해야 한다") {
            LdapQueryBuilder.guessUser("gildong", useEmailBaseLogin = true) { null } shouldBe "gildong"
        }
    }

    describe("LdapQueryBuilder.searchFilterAttribute") {
        it("@가 포함되면 이메일 속성명을 반환해야 한다") {
            LdapQueryBuilder.searchFilterAttribute("gildong@example.com", emailProperty = "mail", loginProperty = "sAMAccountName") shouldBe "mail"
        }

        it("@가 없으면 로그인 속성명을 반환해야 한다") {
            LdapQueryBuilder.searchFilterAttribute("gildong", emailProperty = "mail", loginProperty = "sAMAccountName") shouldBe "sAMAccountName"
        }
    }

    describe("LdapQueryBuilder.properPrincipal") {
        it("@가 포함된 식별자는 그대로 principal로 사용해야 한다") {
            LdapQueryBuilder.properPrincipal("gildong@example.com", userNameProperty = "CN", dnPostfix = "dc=example,dc=com") shouldBe "gildong@example.com"
        }

        it("@가 없으면 CN=식별자,DN접미사 형태로 조립해야 한다") {
            LdapQueryBuilder.properPrincipal("gildong", userNameProperty = "CN", dnPostfix = "dc=example,dc=com") shouldBe "CN=gildong,dc=example,dc=com"
        }
    }

    describe("LdapQueryBuilder.parseLdapUser") {
        fun attrs(vararg pairs: Pair<String, String>): Attributes {
            val attributes = BasicAttributes()
            for ((name, value) in pairs) {
                attributes.put(BasicAttribute(name, value))
            }
            return attributes
        }

        it("부서 정보가 있으면 표시이름에 부서를 대괄호로 붙여야 한다") {
            val result = LdapQueryBuilder.parseLdapUser(
                attrs("displayName" to "홍길동", "mail" to "gildong@example.com", "sAMAccountName" to "gildong", "department" to "개발팀"),
                displayNameProperty = "displayName", emailProperty = "mail",
                loginProperty = "sAMAccountName", departmentProperty = "department", englishNameProperty = null
            )

            result.displayName shouldBe "홍길동"
            result.department shouldBe "개발팀"
            result.email shouldBe "gildong@example.com"
            result.loginId shouldBe "gildong"
            result.fullDisplayName shouldBe "홍길동 [개발팀]"
        }

        it("부서 정보가 없으면 표시이름에 부서를 붙이지 않아야 한다") {
            val result = LdapQueryBuilder.parseLdapUser(
                attrs("displayName" to "홍길동", "mail" to "gildong@example.com", "sAMAccountName" to "gildong"),
                displayNameProperty = "displayName", emailProperty = "mail",
                loginProperty = "sAMAccountName", departmentProperty = "department", englishNameProperty = null
            )

            result.fullDisplayName shouldBe "홍길동"
        }

        it("englishNameProperty가 설정돼 있고 값이 있으면 englishName을 채워야 한다") {
            val result = LdapQueryBuilder.parseLdapUser(
                attrs("displayName" to "홍길동", "mail" to "gildong@example.com", "sAMAccountName" to "gildong", "givenName" to "Gildong"),
                displayNameProperty = "displayName", emailProperty = "mail",
                loginProperty = "sAMAccountName", departmentProperty = "department", englishNameProperty = "givenName"
            )

            result.englishName shouldBe "Gildong"
        }

        it("속성이 존재하지 않으면 빈 문자열로 처리해야 한다") {
            val result = LdapQueryBuilder.parseLdapUser(
                attrs("sAMAccountName" to "gildong"),
                displayNameProperty = "displayName", emailProperty = "mail",
                loginProperty = "sAMAccountName", departmentProperty = "department", englishNameProperty = null
            )

            result.displayName shouldBe ""
            result.email shouldBe ""
        }

        // englishNameProperty가 설정돼 있어도 값 자체가 공백뿐이면(첫 번째 takeIf) englishName은 null이어야 한다.
        it("englishNameProperty가 공백 문자열이면 englishName을 채우지 않아야 한다") {
            val result = LdapQueryBuilder.parseLdapUser(
                attrs("displayName" to "홍길동", "mail" to "gildong@example.com", "sAMAccountName" to "gildong"),
                displayNameProperty = "displayName", emailProperty = "mail",
                loginProperty = "sAMAccountName", departmentProperty = "department", englishNameProperty = "   "
            )

            result.englishName shouldBe null
        }

        // englishNameProperty는 유효하지만 해당 속성의 실제 값이 공백이면(두 번째 takeIf) englishName은 null이어야 한다.
        it("englishNameProperty에 대응하는 속성값이 공백이면 englishName을 채우지 않아야 한다") {
            val result = LdapQueryBuilder.parseLdapUser(
                attrs("displayName" to "홍길동", "mail" to "gildong@example.com", "sAMAccountName" to "gildong", "givenName" to "   "),
                displayNameProperty = "displayName", emailProperty = "mail",
                loginProperty = "sAMAccountName", departmentProperty = "department", englishNameProperty = "givenName"
            )

            result.englishName shouldBe null
        }

        // attributeString()의 attribute.get() ?: "" 엘비스 — 속성은 존재하지만 값 자체가 null인 경우.
        it("속성은 존재하지만 값이 null이면 빈 문자열로 처리해야 한다") {
            val nullValueAttribute = mockk<Attribute>()
            every { nullValueAttribute.get() } returns null
            val attributes = mockk<Attributes>()
            every { attributes.get("displayName") } returns nullValueAttribute
            every { attributes.get(any()) } returns null
            every { attributes.get("displayName") } returns nullValueAttribute

            val result = LdapQueryBuilder.parseLdapUser(
                attributes,
                displayNameProperty = "displayName", emailProperty = "mail",
                loginProperty = "sAMAccountName", departmentProperty = "department", englishNameProperty = null
            )

            result.displayName shouldBe ""
        }

        // attributeString()의 try/catch — 값을 읽는 도중 예외가 발생하면 빈 문자열로 처리해야 한다.
        it("속성값을 읽는 도중 예외가 발생하면 빈 문자열로 처리해야 한다") {
            val throwingAttribute = mockk<Attribute>()
            every { throwingAttribute.get() } throws NamingException("boom")
            val attributes = mockk<Attributes>()
            every { attributes.get(any()) } returns null
            every { attributes.get("displayName") } returns throwingAttribute

            val result = LdapQueryBuilder.parseLdapUser(
                attributes,
                displayNameProperty = "displayName", emailProperty = "mail",
                loginProperty = "sAMAccountName", departmentProperty = "department", englishNameProperty = null
            )

            result.displayName shouldBe ""
        }
    }

    describe("LdapQueryBuilder.isGuestUser") {
        it("prefix 설정이 비어있으면 항상 false여야 한다") {
            LdapQueryBuilder.isGuestUser("guest-gildong", prefixConfig = "") shouldBe false
        }

        it("loginId가 설정된 prefix 중 하나로 시작하면 true여야 한다") {
            LdapQueryBuilder.isGuestUser("guest-gildong", prefixConfig = "temp-, guest-") shouldBe true
        }

        it("loginId가 어떤 prefix로도 시작하지 않으면 false여야 한다") {
            LdapQueryBuilder.isGuestUser("gildong", prefixConfig = "temp-, guest-") shouldBe false
        }

        it("대소문자를 구분하지 않고 매칭해야 한다") {
            LdapQueryBuilder.isGuestUser("GUEST-gildong", prefixConfig = "guest-") shouldBe true
        }
    }
})
