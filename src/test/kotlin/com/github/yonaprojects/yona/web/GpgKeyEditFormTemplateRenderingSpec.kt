package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.gpgkey.GpgKeyRepository
import com.github.yonaprojects.yona.domain.user.Email
import com.github.yonaprojects.yona.domain.user.EmailRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// 실제 gpg 바이너리(gpg --batch --generate-key ... && gpg --armor --export ...)로 만든 진짜
// ed25519 GPG 공개키. UID: "Render Test <gpg-render-test@example.com>".
private const val GPG_KEY_1 = """-----BEGIN PGP PUBLIC KEY BLOCK-----

mDMEap2uPRYJKwYBBAHaRw8BAQdAHTZTJABXok+UKCNfXpJXpRM1gjpVMbZa/uv9
DYScCiu0KVJlbmRlciBUZXN0IDxncGctcmVuZGVyLXRlc3RAZXhhbXBsZS5jb20+
iJAEExYKADgWIQQmrTyRH1qp35AquGlsvA+NBQ9PkwUCap2uPQIbIwULCQgHAgYV
CgkICwIEFgIDAQIeAQIXgAAKCRBsvA+NBQ9Pk1ZfAP9dXJVq3Oduf7nxMAWoK2td
j73DVA0JLWTzuyU6YmVZMwD/d4IU2nVI+JQ+f7vlmJzycFtfYV8TUhcGWh2og+9U
JAU=
=mIdr
-----END PGP PUBLIC KEY BLOCK-----"""
private const val GPG_KEY_1_EMAIL = "gpg-render-test@example.com"

// 새 화면(user/edit_gpg_keys.html)이 실제로 Thymeleaf 렌더링까지 통과하는지 검증.
// SshKeyEditFormTemplateRenderingSpec과 동일한 패턴.
//
// GitHub 컨벤션대로 목록(edit_gpg_keys.html)과 등록 폼(edit_gpg_keys_new.html)을 별개 페이지로
// 분리했고, 등록(POST)은 Post/Redirect/Get 패턴이라 같은 세션으로 리다이렉트를 따라가야 플래시
// 성공 메시지가 보인다.
class GpgKeyEditFormTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val userRepository: UserRepository,
    private val emailRepository: EmailRepository,
    private val gpgKeyRepository: GpgKeyRepository
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc
    private lateinit var owner: User

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()

            owner = userRepository.save(
                User(loginId = "gpgkeyform-owner", name = "GPG키폼소유자", email = GPG_KEY_1_EMAIL)
            )
            emailRepository.save(Email(user = owner, email = GPG_KEY_1_EMAIL, valid = true))
        }

        afterSpec {
            gpgKeyRepository.deleteAll()
            emailRepository.deleteAll()
            userRepository.delete(owner)
        }

        fun authOf(u: User) = user(
            YonaUserDetails(
                id = u.id ?: 0L,
                loginId = u.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )
        )

        describe("GET /user/editform/gpg-keys") {
            it("로그인 사용자에게 200과 목록/탭메뉴를 렌더링해야 하고, 등록 폼 필드는 없어야 한다") {
                val body = mockMvc.perform(get("/user/editform/gpg-keys").with(authOf(owner)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "Add new GPG key"
                body shouldContain "/user/editform/gpg-keys/new"
                body shouldNotContain "frmGpgKeyAdd"
            }
        }

        describe("GET /user/editform/gpg-keys/new") {
            it("로그인 사용자에게 200과 등록 폼을 렌더링해야 한다") {
                val body = mockMvc.perform(get("/user/editform/gpg-keys/new").with(authOf(owner)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "frmGpgKeyAdd"
                body shouldContain "/user/editform/gpg-keys"
            }
        }

        describe("POST /user/editform/gpg-keys -> GET /user/editform/gpg-keys") {
            it("계정 소유로 인증된 이메일이 UID에 있는 GPG 키를 등록하면 목록 화면으로 리다이렉트되고 성공 메시지와 함께 나타나야 한다") {
                val session = MockHttpSession()

                mockMvc.perform(
                    post("/user/editform/gpg-keys").with(authOf(owner)).with(csrf()).session(session)
                        .param("armoredPublicKey", GPG_KEY_1)
                ).andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/user/editform/gpg-keys"))

                val listBody = mockMvc.perform(get("/user/editform/gpg-keys").with(authOf(owner)).session(session))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                listBody shouldContain "GPG key added."
                listBody shouldContain GPG_KEY_1_EMAIL
            }

            it("올바르지 않은 GPG 공개키는 목록이 아니라 등록 폼으로 되돌아가 오류 메시지와 함께 200을 응답해야 한다") {
                val body = mockMvc.perform(
                    post("/user/editform/gpg-keys").with(authOf(owner)).with(csrf())
                        .param("armoredPublicKey", "not-a-valid-gpg-key")
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "frmGpgKeyAdd"
                body shouldContain "alert-error"
            }
        }
    }
}
