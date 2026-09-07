package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.sshkey.SshKeyRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.authority.AuthorityUtils
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

private const val SSH_KEY_1 =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHFAhHu7wL23JgqJtpP8u/JUqCaLm1vcYoohMQFAdpXS render-test@example.com"
private const val SSH_KEY_2 =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAILRyWi6jud2ngJsCWbqDTigEMGZ6zxc+j8wSz5iQL6Bx render-test2@example.com"

// yona-wiki P3-03 Step3 — 새 화면(user/edit_ssh_keys.html)이 실제로 Thymeleaf 렌더링까지 통과하는지
// 검증. ApiTokenEditFormTemplateRenderingSpec과 동일한 패턴.
//
// GitHub 컨벤션대로 목록(edit_ssh_keys.html)과 등록 폼(edit_ssh_keys_new.html)을 별개 페이지로
// 분리했고, 등록(POST)은 Post/Redirect/Get 패턴이라 같은 세션으로 리다이렉트를 따라가야 플래시
// 성공 메시지가 보인다.
class SshKeyEditFormTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val userRepository: UserRepository,
    private val sshKeyRepository: SshKeyRepository
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc
    private lateinit var owner: User

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()

            owner = userRepository.save(
                User(loginId = "sshkeyform-owner", name = "SSH키폼소유자", email = "sshkeyform-owner@example.com")
            )
        }

        afterSpec {
            sshKeyRepository.deleteAll()
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

        describe("GET /user/editform/ssh-keys") {
            it("로그인 사용자에게 200과 목록/탭메뉴를 렌더링해야 하고, 등록 폼 필드는 없어야 한다") {
                val body = mockMvc.perform(get("/user/editform/ssh-keys").with(authOf(owner)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "새 SSH 키 추가"
                body shouldContain "/user/editform/ssh-keys/new"
                body shouldNotContain "frmSshKeyAdd"
            }
        }

        describe("GET /user/editform/ssh-keys/new") {
            it("로그인 사용자에게 200과 등록 폼을 렌더링해야 한다") {
                val body = mockMvc.perform(get("/user/editform/ssh-keys/new").with(authOf(owner)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "frmSshKeyAdd"
                body shouldContain "/user/editform/ssh-keys"
            }
        }

        describe("POST /user/editform/ssh-keys -> GET /user/editform/ssh-keys") {
            it("SSH 키를 등록하면 목록 화면으로 리다이렉트되고, 그 화면에 성공 메시지와 지문이 나타나야 한다") {
                val session = MockHttpSession()

                mockMvc.perform(
                    post("/user/editform/ssh-keys").with(authOf(owner)).session(session)
                        .param("title", "렌더링테스트키")
                        .param("publicKey", SSH_KEY_1)
                ).andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/user/editform/ssh-keys"))

                val listBody = mockMvc.perform(get("/user/editform/ssh-keys").with(authOf(owner)).session(session))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                listBody shouldContain "SSH 키가 추가되었습니다"
                listBody shouldContain "렌더링테스트키"
                listBody shouldContain "SHA256:"
            }

            it("삭제하면 목록에서 사라져야 한다") {
                mockMvc.perform(
                    post("/user/editform/ssh-keys").with(authOf(owner))
                        .param("title", "삭제될키")
                        .param("publicKey", SSH_KEY_2)
                ).andExpect(status().is3xxRedirection)

                val issued = sshKeyRepository.findByUserId(owner.id!!).first { it.title == "삭제될키" }

                mockMvc.perform(post("/user/editform/ssh-keys/${issued.id}/delete").with(authOf(owner)))
                    .andExpect(status().is3xxRedirection)

                val listBody = mockMvc.perform(get("/user/editform/ssh-keys").with(authOf(owner)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                listBody shouldNotContain "삭제될키"
            }

            it("올바르지 않은 공개키는 목록이 아니라 등록 폼으로 되돌아가 오류와 함께 200을 응답해야 한다") {
                val body = mockMvc.perform(
                    post("/user/editform/ssh-keys").with(authOf(owner))
                        .param("title", "잘못된 키")
                        .param("publicKey", "not-a-valid-key")
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "frmSshKeyAdd"
                body shouldContain "alert-error"
                body shouldNotContain "SHA256:not-a-valid-key"
            }
        }
    }
}
