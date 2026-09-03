package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.mail.MailService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional

class UserServiceImplSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val emailRepository = mockk<EmailRepository>()
    val userVerificationRepository = mockk<UserVerificationRepository>()
    val mailService = mockk<MailService>()

    val userService = UserServiceImpl(userRepository, emailRepository, userVerificationRepository, mailService)

    beforeTest {
        clearMocks(userRepository, emailRepository, userVerificationRepository, mailService)
    }

    describe("UserServiceImpl") {
        val testUser = User(id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com")

        describe("findByLoginId") {
            it("존재하는 로그인 아이디면 사용자를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)

                userService.findByLoginId("gildong") shouldBe testUser
            }

            it("존재하지 않는 로그인 아이디면 null을 반환해야 한다") {
                every { userRepository.findByLoginId("nobody") } returns Optional.empty()

                userService.findByLoginId("nobody") shouldBe null
            }
        }

        describe("findByEmail") {
            it("메인 이메일로 조회되면 해당 사용자를 반환해야 한다") {
                every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(testUser)

                userService.findByEmail("gildong@example.com") shouldBe testUser
            }

            it("메인 이메일에 없고 인증된 보조 이메일이면 그 소유자를 반환해야 한다") {
                every { userRepository.findByEmail("sub@example.com") } returns Optional.empty()
                val subEmail = Email(id = 5L, user = testUser, email = "sub@example.com", valid = true)
                every { emailRepository.findByEmailAndValid("sub@example.com", true) } returns subEmail

                userService.findByEmail("sub@example.com") shouldBe testUser
            }

            it("메인/보조 어디에도 없으면 null을 반환해야 한다") {
                every { userRepository.findByEmail("unknown@example.com") } returns Optional.empty()
                every { emailRepository.findByEmailAndValid("unknown@example.com", true) } returns null

                userService.findByEmail("unknown@example.com") shouldBe null
            }
        }

        describe("createUser") {
            it("생성일을 채우고 이름 앞뒤 공백을 제거한 뒤 저장해야 한다") {
                val newUser = User(loginId = "newbie", name = "  새사람  ", email = "newbie@example.com")
                every { userRepository.save(any()) } answers { firstArg() }

                val created = userService.createUser(newUser)

                created.name shouldBe "새사람"
                created.createdDate shouldBe newUser.createdDate
                verify(exactly = 1) { userRepository.save(newUser) }
            }
        }

        describe("isLoginIdExist") {
            it("존재하면 true를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                userService.isLoginIdExist("gildong") shouldBe true
            }

            it("존재하지 않으면 false를 반환해야 한다") {
                every { userRepository.findByLoginId("nobody") } returns Optional.empty()
                userService.isLoginIdExist("nobody") shouldBe false
            }
        }

        describe("isEmailExist") {
            it("메인 이메일로 존재하면 true를 반환해야 한다") {
                every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(testUser)

                userService.isEmailExist("gildong@example.com") shouldBe true

                // 메인 이메일에서 이미 찾았으므로 서브 이메일 조회는 호출되지 않아야 한다
                verify(exactly = 0) { emailRepository.existsByEmailAndValid(any(), any()) }
            }

            it("메인엔 없지만 인증된 보조 이메일로 존재하면 true를 반환해야 한다") {
                every { userRepository.findByEmail("sub@example.com") } returns Optional.empty()
                every { emailRepository.existsByEmailAndValid("sub@example.com", true) } returns true

                userService.isEmailExist("sub@example.com") shouldBe true
            }

            it("어디에도 없으면 false를 반환해야 한다") {
                every { userRepository.findByEmail("unknown@example.com") } returns Optional.empty()
                every { emailRepository.existsByEmailAndValid("unknown@example.com", true) } returns false

                userService.isEmailExist("unknown@example.com") shouldBe false
            }
        }

        describe("addEmail") {
            it("사용자를 찾을 수 없으면 예외를 던져야 한다") {
                every { userRepository.findById(999L) } returns Optional.empty()

                val ex = shouldThrow<IllegalArgumentException> {
                    userService.addEmail(999L, "new@example.com")
                }
                ex.message shouldBe "사용자를 찾을 수 없습니다."
            }

            it("이미 등록된(인증된) 이메일이면 예외를 던져야 한다") {
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userRepository.findByEmail("dup@example.com") } returns Optional.empty()
                every { emailRepository.existsByEmailAndValid("dup@example.com", true) } returns true

                val ex = shouldThrow<IllegalArgumentException> {
                    userService.addEmail(1L, "dup@example.com")
                }
                ex.message shouldBe "이미 등록되었거나 등록 대기 중인 이메일입니다."
            }

            it("사용자가 이미 가지고 있는(대기 중인) 이메일이면 예외를 던져야 한다") {
                val userWithPending = User(id = 2L, loginId = "chulsoo", name = "철수", email = "chulsoo@example.com")
                val pending = Email(id = 20L, user = userWithPending, email = "pending@example.com", valid = false)
                userWithPending.addEmail(pending)

                every { userRepository.findById(2L) } returns Optional.of(userWithPending)
                every { userRepository.findByEmail("pending@example.com") } returns Optional.empty()
                every { emailRepository.existsByEmailAndValid("pending@example.com", true) } returns false

                val ex = shouldThrow<IllegalArgumentException> {
                    userService.addEmail(2L, "pending@example.com")
                }
                ex.message shouldBe "이미 등록되었거나 등록 대기 중인 이메일입니다."
            }

            it("정상적인 신규 이메일이면 미인증 상태로 추가하고 저장해야 한다") {
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userRepository.findByEmail("new-sub@example.com") } returns Optional.empty()
                every { emailRepository.existsByEmailAndValid("new-sub@example.com", true) } returns false
                every { emailRepository.save(any()) } answers { firstArg() }

                val saved = userService.addEmail(1L, "new-sub@example.com")

                saved.email shouldBe "new-sub@example.com"
                saved.valid shouldBe false
                testUser.has("new-sub@example.com") shouldBe true
            }
        }

        describe("deleteEmail") {
            it("이메일을 찾을 수 없으면 예외를 던져야 한다") {
                every { emailRepository.findById(999L) } returns Optional.empty()

                val ex = shouldThrow<IllegalArgumentException> {
                    userService.deleteEmail(1L, 999L)
                }
                ex.message shouldBe "이메일을 찾을 수 없습니다."
            }

            it("다른 사용자의 이메일이면 삭제 권한 예외를 던져야 한다") {
                val other = User(id = 2L, loginId = "chulsoo", name = "철수", email = "chulsoo@example.com")
                val email = Email(id = 10L, user = other, email = "chulsoo-sub@example.com")
                every { emailRepository.findById(10L) } returns Optional.of(email)

                val ex = shouldThrow<IllegalArgumentException> {
                    userService.deleteEmail(1L, 10L)
                }
                ex.message shouldBe "삭제 권한이 없습니다."
            }

            it("본인 소유 이메일이면 정상 삭제해야 한다") {
                val email = Email(id = 10L, user = testUser, email = "gildong-sub@example.com")
                every { emailRepository.findById(10L) } returns Optional.of(email)
                every { emailRepository.delete(email) } just Runs

                userService.deleteEmail(1L, 10L)

                verify(exactly = 1) { emailRepository.delete(email) }
            }
        }

        describe("sendValidationEmail") {
            it("이메일을 찾을 수 없으면 예외를 던져야 한다") {
                every { emailRepository.findById(999L) } returns Optional.empty()

                val ex = shouldThrow<IllegalArgumentException> {
                    userService.sendValidationEmail(1L, 999L, "http://localhost")
                }
                ex.message shouldBe "이메일을 찾을 수 없습니다."
            }

            it("다른 사용자의 이메일이면 권한 예외를 던져야 한다") {
                val other = User(id = 2L, loginId = "chulsoo", name = "철수", email = "chulsoo@example.com")
                val email = Email(id = 10L, user = other, email = "chulsoo-sub@example.com")
                every { emailRepository.findById(10L) } returns Optional.of(email)

                val ex = shouldThrow<IllegalArgumentException> {
                    userService.sendValidationEmail(1L, 10L, "http://localhost")
                }
                ex.message shouldBe "메일을 보낼 권한이 없습니다."
            }

            it("본인 소유 이메일이면 토큰을 채번하고 인증 메일을 발송해야 한다") {
                val email = Email(id = 10L, user = testUser, email = "gildong-sub@example.com")
                every { emailRepository.findById(10L) } returns Optional.of(email)
                val savedSlot = slot<Email>()
                every { emailRepository.save(capture(savedSlot)) } answers { firstArg() }
                every { mailService.sendHtmlMail(any(), any(), any(), any()) } just Runs

                userService.sendValidationEmail(1L, 10L, "http://localhost")

                // 50자리 숫자 토큰이 채번되어야 한다
                savedSlot.captured.token?.length shouldBe 50
                verify(exactly = 1) {
                    mailService.sendHtmlMail(
                        "gildong-sub@example.com",
                        "홍길동",
                        "[Yona] 보조 이메일 주소 인증",
                        match { it.contains("http://localhost/user/emails/10/confirm?token=") }
                    )
                }
            }
        }

        describe("confirmEmail") {
            it("이메일을 찾을 수 없으면 false를 반환해야 한다") {
                every { emailRepository.findById(999L) } returns Optional.empty()

                userService.confirmEmail(999L, "abc") shouldBe false
            }

            it("토큰이 일치하지 않으면 false를 반환하고 저장하지 않아야 한다") {
                val email = Email(id = 10L, user = testUser, email = "sub@example.com", token = "correct-token")
                every { emailRepository.findById(10L) } returns Optional.of(email)

                userService.confirmEmail(10L, "wrong-token") shouldBe false

                verify(exactly = 0) { emailRepository.save(any()) }
            }

            it("토큰이 일치하면 인증 처리하고 동일 이메일의 다른 미인증 건을 정리한 뒤 true를 반환해야 한다") {
                val email = Email(id = 10L, user = testUser, email = "sub@example.com", token = "correct-token")
                val staleDuplicate = Email(id = 11L, user = testUser, email = "sub@example.com", valid = false)
                every { emailRepository.findById(10L) } returns Optional.of(email)
                every { emailRepository.save(email) } returns email
                every { emailRepository.findByEmailAndValidFalse("sub@example.com") } returns listOf(staleDuplicate)
                every { emailRepository.deleteAll(listOf(staleDuplicate)) } just Runs

                userService.confirmEmail(10L, "correct-token") shouldBe true

                email.valid shouldBe true
                verify(exactly = 1) { emailRepository.save(email) }
                verify(exactly = 1) { emailRepository.deleteAll(listOf(staleDuplicate)) }
            }
        }

        describe("setAsMainEmail") {
            it("사용자를 찾을 수 없으면 예외를 던져야 한다") {
                every { userRepository.findById(999L) } returns Optional.empty()

                val ex = shouldThrow<IllegalArgumentException> {
                    userService.setAsMainEmail(999L, 10L)
                }
                ex.message shouldBe "사용자를 찾을 수 없습니다."
            }

            it("이메일을 찾을 수 없으면 예외를 던져야 한다") {
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { emailRepository.findById(999L) } returns Optional.empty()

                val ex = shouldThrow<IllegalArgumentException> {
                    userService.setAsMainEmail(1L, 999L)
                }
                ex.message shouldBe "이메일을 찾을 수 없습니다."
            }

            it("다른 사용자의 이메일이면 변경 권한 예외를 던져야 한다") {
                val other = User(id = 2L, loginId = "chulsoo", name = "철수", email = "chulsoo@example.com")
                val email = Email(id = 10L, user = other, email = "chulsoo-sub@example.com")
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { emailRepository.findById(10L) } returns Optional.of(email)

                val ex = shouldThrow<IllegalArgumentException> {
                    userService.setAsMainEmail(1L, 10L)
                }
                ex.message shouldBe "변경 권한이 없습니다."
            }

            it("본인 소유 서브 이메일이면 메인으로 승격하고 기존 메인은 서브로 보관해야 한다") {
                val user = User(id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com")
                val email = Email(id = 10L, user = user, email = "gildong-sub@example.com")
                user.addEmail(email)

                every { userRepository.findById(1L) } returns Optional.of(user)
                every { emailRepository.findById(10L) } returns Optional.of(email)
                every { userRepository.save(user) } returns user
                val newSubSlot = slot<Email>()
                every { emailRepository.save(capture(newSubSlot)) } answers { firstArg() }
                every { emailRepository.delete(email) } just Runs

                userService.setAsMainEmail(1L, 10L)

                user.email shouldBe "gildong-sub@example.com"
                user.emails.contains(email) shouldBe false
                newSubSlot.captured.email shouldBe "gildong@example.com"
                newSubSlot.captured.valid shouldBe true
                verify(exactly = 1) { emailRepository.delete(email) }
            }
        }

        describe("verifyUser") {
            it("인증정보를 찾을 수 없으면 false를 반환해야 한다") {
                every {
                    userVerificationRepository.findByLoginIdAndVerificationCode("gildong", "code")
                } returns null

                userService.verifyUser("gildong", "code") shouldBe false
            }

            it("유효기간이 지난 인증정보면 삭제하고 false를 반환해야 한다") {
                val expired = UserVerification(
                    id = 1L, user = testUser, loginId = "gildong",
                    verificationCode = "code", timestamp = 0L
                )
                every {
                    userVerificationRepository.findByLoginIdAndVerificationCode("gildong", "code")
                } returns expired
                every { userVerificationRepository.delete(expired) } just Runs

                userService.verifyUser("gildong", "code") shouldBe false

                verify(exactly = 1) { userVerificationRepository.delete(expired) }
                verify(exactly = 0) { userRepository.save(any()) }
            }

            it("유효한 인증정보면 사용자를 활성화하고 인증정보를 삭제한 뒤 true를 반환해야 한다") {
                val target = User(id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com", state = UserState.LOCKED)
                val verification = UserVerification(
                    id = 1L, user = target, loginId = "gildong",
                    verificationCode = "code", timestamp = System.currentTimeMillis()
                )
                every {
                    userVerificationRepository.findByLoginIdAndVerificationCode("gildong", "code")
                } returns verification
                every { userRepository.save(target) } returns target
                every { userVerificationRepository.delete(verification) } just Runs

                userService.verifyUser("gildong", "code") shouldBe true

                target.state shouldBe UserState.ACTIVE
                verify(exactly = 1) { userRepository.save(target) }
                verify(exactly = 1) { userVerificationRepository.delete(verification) }
            }
        }

        describe("createVerification") {
            it("기존 인증정보가 있으면 삭제 후 새로 생성해야 한다") {
                val existing = UserVerification(id = 1L, user = testUser, loginId = "gildong", verificationCode = "old", timestamp = 0L)
                every { userVerificationRepository.findByUser(testUser) } returns existing
                every { userVerificationRepository.delete(existing) } just Runs
                every { userVerificationRepository.save(any()) } answers { firstArg() }

                val created = userService.createVerification(testUser)

                created.loginId shouldBe "gildong"
                created.verificationCode shouldNotBe "old"
                verify(exactly = 1) { userVerificationRepository.delete(existing) }
            }

            it("기존 인증정보가 없으면 삭제 없이 새로 생성해야 한다") {
                every { userVerificationRepository.findByUser(testUser) } returns null
                every { userVerificationRepository.save(any()) } answers { firstArg() }

                val created = userService.createVerification(testUser)

                created.user shouldBe testUser
                verify(exactly = 0) { userVerificationRepository.delete(any()) }
            }
        }

        describe("sendVerificationEmail") {
            it("인증정보를 생성하고 가입 인증 메일을 발송해야 한다") {
                every { userVerificationRepository.findByUser(testUser) } returns null
                val verificationSlot = slot<UserVerification>()
                every { userVerificationRepository.save(capture(verificationSlot)) } answers { firstArg() }
                every { mailService.sendHtmlMail(any(), any(), any(), any()) } just Runs

                userService.sendVerificationEmail(testUser, "http://localhost")

                verify(exactly = 1) {
                    mailService.sendHtmlMail(
                        "gildong@example.com",
                        "홍길동",
                        "[Yona] 회원가입 계정 활성화 인증",
                        match { it.contains("http://localhost/user/verify?loginId=gildong&code=${verificationSlot.captured.verificationCode}") }
                    )
                }
            }
        }
    }

        describe("Coverage addition for UserServiceImpl") {
            it("should handle null email.user.id in deleteEmail") {
                val nullIdUser = User(id = null, loginId = "tester", email = "test@yona.io")
                val email = Email(id = 10L, user = nullIdUser, email = "sub@yona.io")
                every { emailRepository.findById(10L) } returns Optional.of(email)
                
                val ex = shouldThrow<IllegalArgumentException> {
                    userService.deleteEmail(1L, 10L)
                }
                ex.message shouldBe "삭제 권한이 없습니다."
            }

            it("should handle null email.user.id in sendValidationEmail") {
                val nullIdUser = User(id = null, loginId = "tester", email = "test@yona.io")
                val email = Email(id = 10L, user = nullIdUser, email = "sub@yona.io")
                every { emailRepository.findById(10L) } returns Optional.of(email)
                
                val ex = shouldThrow<IllegalArgumentException> {
                    userService.sendValidationEmail(1L, 10L, "http://localhost")
                }
                ex.message shouldBe "메일을 보낼 권한이 없습니다."
            }

            it("should handle null email.user.id in setAsMainEmail") {
                val nullIdUser = User(id = null, loginId = "tester", email = "test@yona.io")
                val email = Email(id = 10L, user = nullIdUser, email = "sub@yona.io")
                every { userRepository.findById(1L) } returns Optional.of(User(id = 1L, loginId = "test", email = "test@yona.io"))
                every { emailRepository.findById(10L) } returns Optional.of(email)
                
                val ex = shouldThrow<IllegalArgumentException> {
                    userService.setAsMainEmail(1L, 10L)
                }
                ex.message shouldBe "변경 권한이 없습니다."
            }
        }
    
})
