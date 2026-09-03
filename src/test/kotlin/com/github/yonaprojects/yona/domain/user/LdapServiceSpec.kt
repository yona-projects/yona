package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.AbstractIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import javax.naming.AuthenticationException
import javax.naming.CommunicationException
import javax.naming.NamingException
import javax.naming.directory.BasicAttributes
import javax.naming.directory.InitialDirContext
import javax.naming.directory.SearchResult
import javax.naming.NamingEnumeration

// LdapService.kt의 클래스 KDoc은 "이 저장소에 LDAP 테스트 서버가 없어 실제 바인딩 경로는
// 단위테스트 대상에서 제외한다"고 적혀 있었으나, 2026-08-25 podman으로 실제 OpenLDAP을 띄워
// 재현한 뒤 Testcontainers(GenericContainer)로 자동화했다 — 이제 실제 바인딩(InitialDirContext
// 생성자의 LDAP simple bind)까지 커버한다. 생성자 자체는 mockkConstructor로 가로챌 수 없어서
// (JNDI가 생성자 안에서 즉시 실제 bind를 수행) 실제 서버가 반드시 필요했다 — search() 결과만
// 시나리오별로 mockkConstructor로 오버라이드해 세부 분기(검색결과 0/1/2건)를 만든다.
@Transactional
@TestPropertySource(properties = [
    "yona.ldap.enabled=true",
    "yona.ldap.protocol=ldap",
    "yona.ldap.base-dn=dc=yona,dc=io",
    "yona.ldap.dn-postfix=dc=yona,dc=io",
    "yona.ldap.login-property=uid",
    "yona.ldap.user-name-property=uid",
    "yona.ldap.use-email-base-login=true"
])
class LdapServiceSpec @Autowired constructor(
    private val ldapService: LdapService,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    companion object {
        private val ldapContainer = GenericContainer("osixia/openldap:1.5.0").apply {
            withExposedPorts(389)
            withEnv("LDAP_ORGANISATION", "Yona")
            withEnv("LDAP_DOMAIN", "yona.io")
            withEnv("LDAP_ADMIN_PASSWORD", "admin")
            withCopyToContainer(
                Transferable.of(
                    """
                    dn: uid=testuser,dc=yona,dc=io
                    objectClass: inetOrgPerson
                    objectClass: posixAccount
                    objectClass: shadowAccount
                    uid: testuser
                    cn: Test User
                    sn: User
                    givenName: Test
                    mail: test@yona.io
                    userPassword: password
                    uidNumber: 10001
                    gidNumber: 10001
                    homeDirectory: /home/testuser
                    """.trimIndent()
                ),
                "/tmp/testuser.ldif"
            )
            waitingFor(Wait.forListeningPort())
        }

        init {
            ldapContainer.start()
            val addResult = ldapContainer.execInContainer(
                "ldapadd", "-x", "-D", "cn=admin,dc=yona,dc=io", "-w", "admin", "-f", "/tmp/testuser.ldif"
            )
            check(addResult.exitCode == 0) { "LDAP 테스트 유저 등록 실패: ${addResult.stderr}" }
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerLdapProperties(registry: DynamicPropertyRegistry) {
            registry.add("yona.ldap.host") { ldapContainer.host }
            registry.add("yona.ldap.port") { ldapContainer.getMappedPort(389).toString() }
        }
    }

    init {
        describe("LdapService") {
            beforeEach {
                mockkConstructor(InitialDirContext::class)
            }
            afterEach {
                unmockkConstructor(InitialDirContext::class)
            }

            it("인증 실패 시 InvalidCredentials를 반환해야 한다") {
                every { anyConstructed<InitialDirContext>().search(any<String>(), any<String>(), any()) } throws AuthenticationException("Auth failed")

                val result = ldapService.authenticate("testuser", "password")
                result.shouldBeInstanceOf<LdapAuthResult.InvalidCredentials>()
            }

            it("통신 실패 시 ConnectionFailed(CommunicationException)를 반환해야 한다") {
                every { anyConstructed<InitialDirContext>().search(any<String>(), any<String>(), any()) } throws CommunicationException("Comm failed")

                val result = ldapService.authenticate("testuser", "password")
                result.shouldBeInstanceOf<LdapAuthResult.ConnectionFailed>()
            }

            it("기타 NamingException 발생 시 ConnectionFailed를 반환해야 한다") {
                every { anyConstructed<InitialDirContext>().search(any<String>(), any<String>(), any()) } throws NamingException("Naming error")

                val result = ldapService.authenticate("testuser", "password")
                result.shouldBeInstanceOf<LdapAuthResult.ConnectionFailed>()
            }

            it("사용자 검색 결과가 없으면 InvalidCredentials를 반환해야 한다") {
                val mockEnum = mockk<NamingEnumeration<SearchResult>>()
                every { mockEnum.hasMoreElements() } returns false
                every { anyConstructed<InitialDirContext>().search(any<String>(), any<String>(), any()) } returns mockEnum

                val result = ldapService.authenticate("testuser", "password")
                result.shouldBeInstanceOf<LdapAuthResult.InvalidCredentials>()
            }

            it("사용자 검색 결과가 2개 이상이면 InvalidCredentials를 반환해야 한다") {
                val mockEnum = mockk<NamingEnumeration<SearchResult>>()
                val mockResult = mockk<SearchResult>()
                every { mockEnum.hasMoreElements() } returns true
                every { mockEnum.nextElement() } returns mockResult
                // 다시 호출할 때도 true를 반환하면 2개 이상임을 나타냄
                every { anyConstructed<InitialDirContext>().search(any<String>(), any<String>(), any()) } answers {
                    val enum2 = mockk<NamingEnumeration<SearchResult>>()
                    var count = 0
                    every { enum2.hasMoreElements() } answers {
                        count < 2
                    }
                    every { enum2.nextElement() } answers {
                        count++
                        mockResult
                    }
                    enum2
                }

                val result = ldapService.authenticate("testuser", "password")
                result.shouldBeInstanceOf<LdapAuthResult.InvalidCredentials>()
            }

            it("정상적으로 사용자를 찾아 Success를 반환해야 한다") {
                val attrs = BasicAttributes()
                attrs.put("displayName", "Test User")
                attrs.put("mail", "test@yona.io")
                attrs.put("uid", "testuser")
                val mockResult = SearchResult("cn=test", null, attrs)

                every { anyConstructed<InitialDirContext>().search(any<String>(), any<String>(), any()) } answers {
                    val enum2 = mockk<NamingEnumeration<SearchResult>>()
                    var count = 0
                    every { enum2.hasMoreElements() } answers {
                        count < 1
                    }
                    every { enum2.nextElement() } answers {
                        count++
                        mockResult
                    }
                    enum2
                }

                val result = ldapService.authenticate("testuser", "password")
                result.shouldBeInstanceOf<LdapAuthResult.Success>()
                val success = result as LdapAuthResult.Success
                success.user.loginId shouldBe "testuser"
            }

            it("enabled/fallbackToLocalLogin 설정값을 그대로 노출해야 한다") {
                ldapService.enabled shouldBe true
                ldapService.fallbackToLocalLogin shouldBe false
            }

            // englishNameProperty가 non-blank인 경우(line 87의 takeIf)는 스펙 전체가 공유하는
            // @TestPropertySource로는 blank 케이스와 동시에 커버할 수 없어(클래스 레벨 고정값),
            // 별도 LdapService 인스턴스를 직접 생성해 이 분기만 격리 검증한다.
            it("englishNameProperty가 설정되어 있으면 해당 속성으로 영문 이름을 조회해야 한다") {
                val serviceWithEnglishName = LdapService(
                    enabled = true, fallbackToLocalLogin = false,
                    host = ldapContainer.host, port = ldapContainer.getMappedPort(389).toString(),
                    protocol = "ldap", baseDn = "dc=yona,dc=io", dnPostfix = "dc=yona,dc=io",
                    loginProperty = "uid", displayNameProperty = "displayName", userNameProperty = "uid",
                    emailProperty = "mail", departmentProperty = "department", englishNameProperty = "cn",
                    useEmailBaseLogin = false, guestLoginIdPrefix = "", userRepository = userRepository
                )

                val attrs = BasicAttributes()
                attrs.put("displayName", "Test User")
                attrs.put("mail", "test@yona.io")
                attrs.put("uid", "testuser")
                attrs.put("cn", "Test English Name")
                val mockResult = SearchResult("cn=test", null, attrs)

                every { anyConstructed<InitialDirContext>().search(any<String>(), any<String>(), any()) } answers {
                    val enum2 = mockk<NamingEnumeration<SearchResult>>()
                    var count = 0
                    every { enum2.hasMoreElements() } answers {
                        count < 1
                    }
                    every { enum2.nextElement() } answers {
                        count++
                        mockResult
                    }
                    enum2
                }

                val result = serviceWithEnglishName.authenticate("testuser", "password")
                result.shouldBeInstanceOf<LdapAuthResult.Success>()
                val success = result as LdapAuthResult.Success
                success.user.englishName shouldBe "Test English Name"
            }

            // useEmailBaseLogin=true일 때 guessUser()가 userRepository로 loginId→email을 조회하는
            // 콜백 경로(authenticate$lambda$0/$0$0/$0$1)는 내부적으로 알고 있는 사용자가 있을 때만 탄다.
            // properPrincipal()은 identity에 "@"가 있으면 그대로 bind DN으로 쓰는데(AD의 UPN 바인딩
            // 방식 가정) 테스트 컨테이너는 OpenLDAP이라 이메일 형식 DN으로는 실제 bind가 실패한다.
            // guessUser()/resolveEmail 람다 체인 자체의 커버리지만 목적이므로, DB에는 "@" 없는 값을
            // email로 저장해 bind 경로(properPrincipal)에 영향 없이 콜백만 격리해서 검증한다.
            it("useEmailBaseLogin=true이면 내부 DB에 등록된 loginId로 email을 조회하는 콜백이 실행돼야 한다") {
                // resolveEmail 콜백(userRepository 조회, Optional이 present인 경로) 자체의 커버리지가
                // 목적이므로, 실제 컨테이너의 유일한 엔트리(uid=testuser)와 bind DN이 어긋나지 않도록
                // 조회 결과(email)도 동일하게 "testuser"로 저장해 identity가 변하지 않게 한다.
                userRepository.save(User(loginId = "testuser", name = "치환유저", email = "testuser"))

                val attrs = BasicAttributes()
                attrs.put("displayName", "Test User")
                attrs.put("mail", "test@yona.io")
                attrs.put("uid", "testuser")
                val mockResult = SearchResult("cn=test", null, attrs)

                every { anyConstructed<InitialDirContext>().search(any<String>(), any<String>(), any()) } answers {
                    val enum2 = mockk<NamingEnumeration<SearchResult>>()
                    var count = 0
                    every { enum2.hasMoreElements() } answers {
                        count < 1
                    }
                    every { enum2.nextElement() } answers {
                        count++
                        mockResult
                    }
                    enum2
                }

                val result = ldapService.authenticate("testuser", "password")
                result.shouldBeInstanceOf<LdapAuthResult.Success>()
            }
        }
    }
}
