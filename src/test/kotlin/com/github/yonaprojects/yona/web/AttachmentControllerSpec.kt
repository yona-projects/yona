package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.config.security.AccessControl
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

import org.hamcrest.Matchers.containsString
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.File
import java.time.Instant
import java.util.Optional
import io.mockk.clearMocks
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.user.UserState

class AttachmentControllerSpec : DescribeSpec({
    val attachmentService = mockk<AttachmentService>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val userRepository = mockk<UserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val accessControl = mockk<AccessControl>()

    val attachmentController = AttachmentController(
        attachmentService,
        attachmentRepository,
        userRepository,
        issueRepository,
        postingRepository,
        milestoneRepository,
        projectUserRepository,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(attachmentController).build()

    beforeTest {
        clearMocks(
            attachmentService,
            attachmentRepository,
            userRepository,
            issueRepository,
            postingRepository,
            milestoneRepository,
            projectUserRepository,
            accessControl
        )
    }

    describe("AttachmentController API 단위 테스트") {
        val loginUser = User(id = 1L, loginId = "tester", name = "테스터")
        val userAuth = UsernamePasswordAuthenticationToken("tester", "password")

        val attachment = Attachment(
            id = 100L,
            name = "test-file.txt",
            hash = "somehash123",
            containerType = ResourceType.ISSUE_POST,
            containerId = "10",
            mimeType = "text/plain",
            size = 12L,
            createdDate = Instant.now(),
            ownerLoginId = "tester"
        )

        describe("POST /files (파일 업로드)") {
            it("로그인된 사용자가 파일을 전송하면 201 Created와 함께 파일 정보 JSON을 반환한다") {
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    attachmentService.store(
                        any(),
                        "test-file.txt",
                        ResourceType.NOT_A_RESOURCE,
                        "",
                        "tester"
                    )
                } returns (attachment to true)

                val multipartFile = MockMultipartFile(
                    "filePath",
                    "test-file.txt",
                    "text/plain",
                    "Hello World".toByteArray()
                )

                mockMvc.perform(
                    multipart("/files")
                        .file(multipartFile)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
                    .andExpect(header().string("Location", "/files/100"))
                    .andExpect(jsonPath("$.id").value("100"))
                    .andExpect(jsonPath("$.name").value("test-file.txt"))
                    .andExpect(jsonPath("$.url").value("/files/100"))
            }

            // yona AttachmentApp.java:119-128 uploadFile()의 isCreated 분기(dedup 시 200, 신규 시
            // 201) 대응 (P2-24). AttachmentService.store()가 dedup으로 기존 첨부를 재사용하면
            // isNew=false를 반환하므로, 컨트롤러는 이를 그대로 응답 코드에 반영해야 한다.
            it("동일 첨부가 이미 존재해 dedup되면 200 OK를 반환한다 (P2-24)") {
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    attachmentService.store(
                        any(),
                        "test-file.txt",
                        ResourceType.NOT_A_RESOURCE,
                        "",
                        "tester"
                    )
                } returns (attachment to false)

                val multipartFile = MockMultipartFile(
                    "filePath",
                    "test-file.txt",
                    "text/plain",
                    "Hello World".toByteArray()
                )

                mockMvc.perform(
                    multipart("/files")
                        .file(multipartFile)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(header().string("Location", "/files/100"))
                    .andExpect(jsonPath("$.id").value("100"))
            }
        }

        describe("GET /files/{id} (파일 다운로드)") {
            it("존재하는 파일 ID로 요청하면 파일 스트림과 적절한 헤더를 반환한다") {
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { accessControl.isAllowedAttachment(loginUser, attachment, Operation.READ) } returns true

                val tempFile = File.createTempFile("yona-test", "txt")
                tempFile.writeText("Hello World")
                tempFile.deleteOnExit()

                every { attachmentService.getFile(attachment) } returns tempFile

                mockMvc.perform(
                    get("/files/100")
                        .param("action", "download")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(header().string("Content-Type", "text/plain"))
                    .andExpect(header().string("Content-Disposition", containsString("filename=\"test-file.txt\"")))
                    .andExpect(content().string("Hello World"))
            }

            it("If-None-Match 헤더 값이 파일 ETag와 일치하면 304 Not Modified를 반환한다") {
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { accessControl.isAllowedAttachment(loginUser, attachment, Operation.READ) } returns true

                val eTag = "\"somehash123-inline\""

                mockMvc.perform(
                    get("/files/100")
                        .header("If-None-Match", eTag)
                        .principal(userAuth)
                )
                    .andExpect(status().isNotModified)
            }

            it("읽기 권한이 없으면 403 Forbidden을 반환한다 (P1-96, 보안)") {
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { accessControl.isAllowedAttachment(loginUser, attachment, Operation.READ) } returns false

                mockMvc.perform(
                    get("/files/100")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }
        }

        describe("POST /files/{id} (파일 삭제)") {
            it("삭제 요청 파라미터 _method=delete를 전달하면 파일을 삭제하고 성공 메시지를 반환한다") {
                val project = mockk<Project>()
                val issue = mockk<Issue>()
                every { issue.project } returns project
                every { issue.authorLoginId } returns "tester"
                every { issueRepository.findById(10L) } returns Optional.of(issue)
                every { accessControl.isAllowedToUpdateIssue(loginUser, project, "tester") } returns true

                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { attachmentService.delete(attachment) } returns Unit
                every { attachmentRepository.existsByHash("somehash123") } returns false

                mockMvc.perform(
                    post("/files/100")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(containsString("removed successfully")))
            }

            // yona AccessControl.java:250-263 isProjectResourceAllowed()의 ATTACHMENT 케이스(컨테이너의
            // UPDATE 권한으로 위임) 대응 (P1-130). 업로더 본인이 아니어도 커밋/리뷰 댓글의 UPDATE 권한이
            // 있는(=프로젝트 멤버) 사용자면 첨부파일을 삭제할 수 있어야 한다.
            it("커밋 댓글 첨부파일은 업로더 본인이 아니어도 컨테이너 UPDATE 권한이 있으면 삭제할 수 있다") {
                val commitCommentAttachment = Attachment(
                    id = 200L,
                    name = "commit-attach.png",
                    hash = "commithash456",
                    containerType = ResourceType.COMMIT_COMMENT,
                    containerId = "50",
                    mimeType = "image/png",
                    size = 34L,
                    createdDate = Instant.now(),
                    ownerLoginId = "someone-else"
                )

                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(200L) } returns Optional.of(commitCommentAttachment)
                every {
                    accessControl.isAllowedAttachment(loginUser, commitCommentAttachment, Operation.UPDATE)
                } returns true
                every { attachmentService.delete(commitCommentAttachment) } returns Unit
                every { attachmentRepository.existsByHash("commithash456") } returns false

                mockMvc.perform(
                    post("/files/200")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(containsString("removed successfully")))
            }
        }

        describe("GET /files (파일 목록 조회)") {
            it("컨테이너 타입과 ID로 조회 시 첨부된 파일 목록 JSON을 반환한다") {
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    accessControl.isAllowedAttachment(loginUser, match { it.containerType == ResourceType.ISSUE_POST && it.containerId == "10" }, Operation.READ)
                } returns true
                every {
                    attachmentRepository.findByContainerTypeAndContainerId(
                        ResourceType.ISSUE_POST,
                        "10"
                    )
                } returns listOf(attachment)

                mockMvc.perform(
                    get("/files")
                        .param("containerType", "ISSUE_POST")
                        .param("containerId", "10")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.attachments[0].id").value("100"))
                    .andExpect(jsonPath("$.attachments[0].name").value("test-file.txt"))
            }

            it("컨테이너 읽기 권한이 없으면 403 Forbidden을 반환한다 (P1-96, 보안)") {
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    accessControl.isAllowedAttachment(loginUser, match { it.containerType == ResourceType.ISSUE_POST && it.containerId == "10" }, Operation.READ)
                } returns false

                mockMvc.perform(
                    get("/files")
                        .param("containerType", "ISSUE_POST")
                        .param("containerId", "10")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            // containerType/containerId 미지정 시 조기 반환하는 두 OR 조건 분기를 각각 짚는다.
            it("containerType 파라미터가 없으면 빈 목록을 반환한다") {
                mockMvc.perform(
                    get("/files")
                        .param("containerId", "10")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.attachments").isEmpty)
            }

            it("containerId 파라미터가 없으면 빈 목록을 반환한다") {
                mockMvc.perform(
                    get("/files")
                        .param("containerType", "ISSUE_POST")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.attachments").isEmpty)
            }

            it("containerType이 공백뿐이면(null 아님) 빈 목록을 반환한다") {
                mockMvc.perform(
                    get("/files")
                        .param("containerType", "   ")
                        .param("containerId", "10")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.attachments").isEmpty)
            }

            it("목록의 첨부 id/size가 null이면 각각 \"\"/\"0\"으로 응답해야 한다") {
                val noIdSizeAttachment = Attachment(
                    id = null, name = "no-id.txt", hash = "nohash",
                    containerType = ResourceType.ISSUE_POST, containerId = "10",
                    mimeType = "text/plain", size = null,
                    createdDate = Instant.now(), ownerLoginId = "tester"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    accessControl.isAllowedAttachment(loginUser, match { it.containerType == ResourceType.ISSUE_POST && it.containerId == "10" }, Operation.READ)
                } returns true
                every {
                    attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, "10")
                } returns listOf(noIdSizeAttachment)

                mockMvc.perform(
                    get("/files")
                        .param("containerType", "ISSUE_POST")
                        .param("containerId", "10")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.attachments[0].id").value(""))
                    .andExpect(jsonPath("$.attachments[0].size").value("0"))
            }

            it("유효하지 않은 containerType 문자열이면 NOT_A_RESOURCE로 처리된다") {
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    accessControl.isAllowedAttachment(loginUser, match { it.containerType == ResourceType.NOT_A_RESOURCE && it.containerId == "10" }, Operation.READ)
                } returns true
                every {
                    attachmentRepository.findByContainerTypeAndContainerId(ResourceType.NOT_A_RESOURCE, "10")
                } returns emptyList()

                mockMvc.perform(
                    get("/files")
                        .param("containerType", "NO_SUCH_TYPE")
                        .param("containerId", "10")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.attachments").isEmpty)
            }

            it("인증되지 않은 사용자도 컨테이너 읽기 권한이 있으면 목록을 조회할 수 있다") {
                every {
                    accessControl.isAllowedAttachment(null, match { it.containerType == ResourceType.ISSUE_POST && it.containerId == "10" }, Operation.READ)
                } returns true
                every {
                    attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, "10")
                } returns listOf(attachment)

                mockMvc.perform(
                    get("/files")
                        .param("containerType", "ISSUE_POST")
                        .param("containerId", "10")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.attachments[0].id").value("100"))
            }

            it("첨부파일의 id/mimeType/size가 없으면 기본값(빈 문자열, \"0\")으로 응답한다") {
                val bareAttachment = Attachment(
                    id = null,
                    name = "no-meta.bin",
                    hash = "baremetahash",
                    containerType = ResourceType.ISSUE_POST,
                    containerId = "10",
                    mimeType = null,
                    size = null,
                    ownerLoginId = "tester"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    accessControl.isAllowedAttachment(loginUser, match { it.containerType == ResourceType.ISSUE_POST && it.containerId == "10" }, Operation.READ)
                } returns true
                every {
                    attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, "10")
                } returns listOf(bareAttachment)

                mockMvc.perform(
                    get("/files")
                        .param("containerType", "ISSUE_POST")
                        .param("containerId", "10")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.attachments[0].id").value(""))
                    .andExpect(jsonPath("$.attachments[0].mimeType").value(""))
                    .andExpect(jsonPath("$.attachments[0].size").value("0"))
            }
        }

        describe("POST /files (findUploader 우선순위 분기)") {
            val multipartFile = MockMultipartFile(
                "filePath",
                "test-file.txt",
                "text/plain",
                "Hello World".toByteArray()
            )

            it("authorEmail/authorLoginId/principal이 모두 없으면 익명 사용자로 간주해 403을 반환한다") {
                mockMvc.perform(
                    multipart("/files").file(multipartFile)
                )
                    .andExpect(status().isForbidden)
            }

            it("principal은 있으나 로그인 사용자를 찾을 수 없으면 익명 처리되어 403을 반환한다") {
                every { userRepository.findByLoginId("tester") } returns Optional.empty()

                mockMvc.perform(
                    multipart("/files")
                        .file(multipartFile)
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("authorEmail로 사용자를 찾으면 해당 사용자를 업로더로 사용한다") {
                val emailUser = User(id = 5L, loginId = "email-user", name = "이메일유저")
                every { userRepository.findByEmail("email-user@example.com") } returns Optional.of(emailUser)
                every {
                    attachmentService.store(any(), "test-file.txt", ResourceType.NOT_A_RESOURCE, "", "email-user")
                } returns (attachment to true)

                mockMvc.perform(
                    multipart("/files")
                        .file(multipartFile)
                        .param("authorEmail", "email-user@example.com")
                )
                    .andExpect(status().isCreated)
            }

            it("authorEmail로 찾은 사용자가 anonymous이면 건너뛰고 authorLoginId로 대체한다") {
                val anonByEmail = User(id = 6L, loginId = "anonymous", name = "익명")
                every { userRepository.findByEmail("anon@example.com") } returns Optional.of(anonByEmail)
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    attachmentService.store(any(), "test-file.txt", ResourceType.NOT_A_RESOURCE, "", "tester")
                } returns (attachment to true)

                mockMvc.perform(
                    multipart("/files")
                        .file(multipartFile)
                        .param("authorEmail", "anon@example.com")
                        .param("authorLoginId", "tester")
                )
                    .andExpect(status().isCreated)
            }

            it("authorEmail로 사용자를 찾지 못하면 principal 기준으로 대체된다") {
                every { userRepository.findByEmail("notfound@example.com") } returns Optional.empty()
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    attachmentService.store(any(), "test-file.txt", ResourceType.NOT_A_RESOURCE, "", "tester")
                } returns (attachment to true)

                mockMvc.perform(
                    multipart("/files")
                        .file(multipartFile)
                        .param("authorEmail", "notfound@example.com")
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
            }

            it("authorLoginId로 사용자를 찾으면 해당 사용자를 업로더로 사용한다") {
                val loginIdUser = User(id = 7L, loginId = "login-user", name = "로그인유저")
                every { userRepository.findByLoginId("login-user") } returns Optional.of(loginIdUser)
                every {
                    attachmentService.store(any(), "test-file.txt", ResourceType.NOT_A_RESOURCE, "", "login-user")
                } returns (attachment to true)

                mockMvc.perform(
                    multipart("/files")
                        .file(multipartFile)
                        .param("authorLoginId", "login-user")
                )
                    .andExpect(status().isCreated)
            }

            it("authorEmail이 공백뿐이면(null 아님) 없는 것처럼 처리해 authorLoginId로 넘어간다") {
                val loginIdUser2 = User(id = 8L, loginId = "login-user2", name = "로그인유저2")
                every { userRepository.findByLoginId("login-user2") } returns Optional.of(loginIdUser2)
                every {
                    attachmentService.store(any(), "test-file.txt", ResourceType.NOT_A_RESOURCE, "", "login-user2")
                } returns (attachment to true)

                mockMvc.perform(
                    multipart("/files")
                        .file(multipartFile)
                        .param("authorEmail", "   ")
                        .param("authorLoginId", "login-user2")
                )
                    .andExpect(status().isCreated)
            }

            it("authorLoginId가 공백뿐이면(null 아님) 없는 것처럼 처리해 principal로 넘어간다") {
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    attachmentService.store(any(), "test-file.txt", ResourceType.NOT_A_RESOURCE, "", "tester")
                } returns (attachment to true)

                mockMvc.perform(
                    multipart("/files")
                        .file(multipartFile)
                        .param("authorLoginId", "   ")
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
            }

            it("authorLoginId로 찾은 사용자가 anonymous이면 건너뛰고 principal로 대체한다") {
                val anonByLoginId = User(id = 9L, loginId = "anonymous", name = "익명2")
                every { userRepository.findByLoginId("anon-login") } returns Optional.of(anonByLoginId)
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    attachmentService.store(any(), "test-file.txt", ResourceType.NOT_A_RESOURCE, "", "tester")
                } returns (attachment to true)

                mockMvc.perform(
                    multipart("/files")
                        .file(multipartFile)
                        .param("authorLoginId", "anon-login")
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
            }

            it("업로드된 첨부의 size가 null이면 응답의 size는 \"0\"이어야 한다") {
                val noSizeAttachment = Attachment(
                    id = 103L, name = "test-file.txt", hash = "nosizehash",
                    containerType = ResourceType.NOT_A_RESOURCE, containerId = "",
                    mimeType = "text/plain", size = null,
                    createdDate = Instant.now(), ownerLoginId = "tester"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    attachmentService.store(any(), "test-file.txt", ResourceType.NOT_A_RESOURCE, "", "tester")
                } returns (noSizeAttachment to true)

                mockMvc.perform(
                    multipart("/files")
                        .file(multipartFile)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.size").value("0"))
            }

            it("authorLoginId로 사용자를 찾지 못하면 principal 기준으로 대체된다") {
                every { userRepository.findByLoginId("ghost") } returns Optional.empty()
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    attachmentService.store(any(), "test-file.txt", ResourceType.NOT_A_RESOURCE, "", "tester")
                } returns (attachment to true)

                mockMvc.perform(
                    multipart("/files")
                        .file(multipartFile)
                        .param("authorLoginId", "ghost")
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
            }

            // MultipartFile.originalFilename이 실제로 null을 반환하는 구현체가 있을 수 있어(예:
            // 일부 서블릿 컨테이너 구현), MockMultipartFile(null로는 재현 불가 - 내부에서 ""로 치환됨)
            // 대신 mockk로 직접 originalFilename=null을 스텁해 컨트롤러 메서드를 직접 호출한다.
            it("MultipartFile.originalFilename이 null이면 'unknown'으로 정규화한다") {
                val nullNameFile = mockk<MultipartFile>()
                every { nullNameFile.originalFilename } returns null
                every { nullNameFile.inputStream } returns "Hello".byteInputStream()

                val noMetaAttachment = Attachment(
                    id = 101L,
                    name = "unknown",
                    hash = "nometahash",
                    containerType = ResourceType.NOT_A_RESOURCE,
                    containerId = "",
                    mimeType = null,
                    size = null,
                    ownerLoginId = "tester"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    attachmentService.store(any(), "unknown", ResourceType.NOT_A_RESOURCE, "", "tester")
                } returns (noMetaAttachment to true)

                val response = attachmentController.uploadFile(nullNameFile, null, null, userAuth)

                response.statusCode.value() shouldBe 201
                response.body?.get("mimeType") shouldBe ""
                response.body?.get("size") shouldBe "0"
            }
        }

        describe("GET /files/{id} (getFile 추가 분기)") {
            it("존재하지 않는 첨부파일 ID로 요청하면 404 Not Found를 반환한다") {
                every { attachmentRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    get("/files/999")
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            it("파일이 파일시스템에 존재하지 않으면 500 Internal Server Error를 반환한다") {
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { accessControl.isAllowedAttachment(loginUser, attachment, Operation.READ) } returns true
                every { attachmentService.getFile(attachment) } returns File("/no/such/path/does-not-exist.bin")

                mockMvc.perform(
                    get("/files/100")
                        .principal(userAuth)
                )
                    .andExpect(status().isInternalServerError)
            }

            it("If-None-Match 헤더 값이 ETag와 다르면 304가 아닌 정상 응답을 반환한다") {
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { accessControl.isAllowedAttachment(loginUser, attachment, Operation.READ) } returns true

                val tempFile = File.createTempFile("yona-test", "txt")
                tempFile.writeText("Hello World")
                tempFile.deleteOnExit()
                every { attachmentService.getFile(attachment) } returns tempFile

                mockMvc.perform(
                    get("/files/100")
                        .header("If-None-Match", "\"different-etag\"")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("인증되지 않은 사용자도 컨테이너 읽기 권한이 있으면 파일을 조회할 수 있다") {
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { accessControl.isAllowedAttachment(null, attachment, Operation.READ) } returns true

                val tempFile = File.createTempFile("yona-test", "txt")
                tempFile.writeText("Hello World")
                tempFile.deleteOnExit()
                every { attachmentService.getFile(attachment) } returns tempFile

                mockMvc.perform(get("/files/100"))
                    .andExpect(status().isOk)
            }

            it("principal은 있으나 로그인 사용자를 찾을 수 없어도 컨테이너 읽기 권한이 있으면 조회할 수 있다") {
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { userRepository.findByLoginId("tester") } returns Optional.empty()
                every { accessControl.isAllowedAttachment(null, attachment, Operation.READ) } returns true

                val tempFile = File.createTempFile("yona-test", "txt")
                tempFile.writeText("Hello World")
                tempFile.deleteOnExit()
                every { attachmentService.getFile(attachment) } returns tempFile

                mockMvc.perform(
                    get("/files/100")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }
        }

        describe("POST /files/{id} (deleteFile 추가 분기)") {
            it("_method 파라미터가 delete가 아니면 400 Bad Request를 반환한다") {
                mockMvc.perform(
                    post("/files/100")
                        .param("_method", "put")
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(content().string(containsString("_method must be 'delete'.")))
            }

            it("_method 파라미터가 없으면 400 Bad Request를 반환한다") {
                mockMvc.perform(
                    post("/files/100")
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)
            }

            it("인증되지 않은 사용자가 삭제를 요청하면 401 Unauthorized를 반환한다") {
                mockMvc.perform(
                    post("/files/100")
                        .param("_method", "delete")
                )
                    .andExpect(status().isUnauthorized)
            }

            it("로그인 사용자를 찾을 수 없으면 401 Unauthorized를 반환한다") {
                every { userRepository.findByLoginId("tester") } returns Optional.empty()

                mockMvc.perform(
                    post("/files/100")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 첨부파일 ID면 404 Not Found를 반환한다") {
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    post("/files/999")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            it("USER 타입 첨부는 업로더 본인이면 삭제할 수 있다") {
                val userAttachment = Attachment(
                    id = 300L, name = "avatar.png", hash = "userhash",
                    containerType = ResourceType.USER, containerId = "1", ownerLoginId = "tester"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(300L) } returns Optional.of(userAttachment)
                every { attachmentService.delete(userAttachment) } returns Unit
                every { attachmentRepository.existsByHash("userhash") } returns false

                mockMvc.perform(
                    post("/files/300")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("USER 타입 첨부는 업로더 본인이 아니어도 사이트관리자면 삭제할 수 있다") {
                val siteAdmin = User(id = 2L, loginId = "admin", name = "관리자", state = UserState.SITE_ADMIN)
                val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")
                val userAttachment = Attachment(
                    id = 301L, name = "avatar2.png", hash = "userhash2",
                    containerType = ResourceType.USER, containerId = "1", ownerLoginId = "someone-else"
                )
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteAdmin)
                every { attachmentRepository.findById(301L) } returns Optional.of(userAttachment)
                every { attachmentService.delete(userAttachment) } returns Unit
                every { attachmentRepository.existsByHash("userhash2") } returns false

                mockMvc.perform(
                    post("/files/301")
                        .param("_method", "delete")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
            }

            it("USER 타입 첨부는 업로더 본인도 아니고 사이트관리자도 아니면 403 Forbidden을 반환한다") {
                val userAttachment = Attachment(
                    id = 302L, name = "avatar3.png", hash = "userhash3",
                    containerType = ResourceType.USER, containerId = "1", ownerLoginId = "someone-else"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(302L) } returns Optional.of(userAttachment)

                mockMvc.perform(
                    post("/files/302")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("NOT_A_RESOURCE 타입 첨부도 업로더 본인이면 삭제할 수 있다") {
                val notAResourceAttachment = Attachment(
                    id = 303L, name = "raw.bin", hash = "narhash",
                    containerType = ResourceType.NOT_A_RESOURCE, containerId = "", ownerLoginId = "tester"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(303L) } returns Optional.of(notAResourceAttachment)
                every { attachmentService.delete(notAResourceAttachment) } returns Unit
                every { attachmentRepository.existsByHash("narhash") } returns false

                mockMvc.perform(
                    post("/files/303")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("ISSUE_POST containerId가 숫자로 변환되지 않으면 403 Forbidden을 반환한다") {
                val issueAttachment = Attachment(
                    id = 400L, name = "issue-attach.png", hash = "issuehash",
                    containerType = ResourceType.ISSUE_POST, containerId = "abc", ownerLoginId = "someone-else"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(400L) } returns Optional.of(issueAttachment)

                mockMvc.perform(
                    post("/files/400")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("ISSUE_POST 이슈를 찾을 수 없으면 403 Forbidden을 반환한다") {
                val issueAttachment = Attachment(
                    id = 401L, name = "issue-attach2.png", hash = "issuehash2",
                    containerType = ResourceType.ISSUE_POST, containerId = "20", ownerLoginId = "someone-else"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(401L) } returns Optional.of(issueAttachment)
                every { issueRepository.findById(20L) } returns Optional.empty()

                mockMvc.perform(
                    post("/files/401")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("ISSUE_POST 수정 권한이 없으면 403 Forbidden을 반환한다") {
                val project = mockk<Project>()
                val issue = mockk<Issue>()
                val issueAttachment = Attachment(
                    id = 402L, name = "issue-attach3.png", hash = "issuehash3",
                    containerType = ResourceType.ISSUE_POST, containerId = "21", ownerLoginId = "someone-else"
                )
                every { issue.project } returns project
                every { issue.authorLoginId } returns "someone"
                every { issueRepository.findById(21L) } returns Optional.of(issue)
                every { accessControl.isAllowedToUpdateIssue(loginUser, project, "someone") } returns false
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(402L) } returns Optional.of(issueAttachment)

                mockMvc.perform(
                    post("/files/402")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("BOARD_POST 게시글 수정 권한이 있으면 삭제할 수 있다") {
                val project = mockk<Project>()
                val posting = mockk<Posting>()
                val postingAttachment = Attachment(
                    id = 500L, name = "board-attach.png", hash = "boardhash",
                    containerType = ResourceType.BOARD_POST, containerId = "30", ownerLoginId = "someone-else"
                )
                every { posting.project } returns project
                every { posting.authorLoginId } returns "someone"
                every { postingRepository.findById(30L) } returns Optional.of(posting)
                every { accessControl.isAllowedToUpdatePosting(loginUser, project, "someone") } returns true
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(500L) } returns Optional.of(postingAttachment)
                every { attachmentService.delete(postingAttachment) } returns Unit
                every { attachmentRepository.existsByHash("boardhash") } returns false

                mockMvc.perform(
                    post("/files/500")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("BOARD_POST의 containerId가 숫자가 아니면 403 Forbidden을 반환한다") {
                val postingAttachmentBadId = Attachment(
                    id = 503L, name = "board-attach-bad.png", hash = "boardhashbad",
                    containerType = ResourceType.BOARD_POST, containerId = "not-a-number", ownerLoginId = "someone-else"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(503L) } returns Optional.of(postingAttachmentBadId)

                mockMvc.perform(
                    post("/files/503")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("BOARD_POST 게시글을 찾을 수 없으면 403 Forbidden을 반환한다") {
                val postingAttachment = Attachment(
                    id = 501L, name = "board-attach2.png", hash = "boardhash2",
                    containerType = ResourceType.BOARD_POST, containerId = "31", ownerLoginId = "someone-else"
                )
                every { postingRepository.findById(31L) } returns Optional.empty()
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(501L) } returns Optional.of(postingAttachment)

                mockMvc.perform(
                    post("/files/501")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("BOARD_POST 게시글 수정 권한이 없으면 403 Forbidden을 반환한다") {
                val project = mockk<Project>()
                val posting = mockk<Posting>()
                val postingAttachment = Attachment(
                    id = 502L, name = "board-attach3.png", hash = "boardhash3",
                    containerType = ResourceType.BOARD_POST, containerId = "32", ownerLoginId = "someone-else"
                )
                every { posting.project } returns project
                every { posting.authorLoginId } returns "someone"
                every { postingRepository.findById(32L) } returns Optional.of(posting)
                every { accessControl.isAllowedToUpdatePosting(loginUser, project, "someone") } returns false
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(502L) } returns Optional.of(postingAttachment)

                mockMvc.perform(
                    post("/files/502")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("MILESTONE 수정 권한이 있으면 삭제할 수 있다") {
                val project = mockk<Project>()
                val milestone = mockk<Milestone>()
                val milestoneAttachment = Attachment(
                    id = 600L, name = "milestone-attach.png", hash = "milestonehash",
                    containerType = ResourceType.MILESTONE, containerId = "40", ownerLoginId = "someone-else"
                )
                every { milestone.project } returns project
                every { milestoneRepository.findById(40L) } returns Optional.of(milestone)
                every { accessControl.isAllowedToUpdateMilestone(loginUser, project) } returns true
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(600L) } returns Optional.of(milestoneAttachment)
                every { attachmentService.delete(milestoneAttachment) } returns Unit
                every { attachmentRepository.existsByHash("milestonehash") } returns false

                mockMvc.perform(
                    post("/files/600")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("MILESTONE의 containerId가 숫자가 아니면 403 Forbidden을 반환한다") {
                val milestoneAttachmentBadId = Attachment(
                    id = 603L, name = "milestone-attach-bad.png", hash = "milestonehashbad",
                    containerType = ResourceType.MILESTONE, containerId = "not-a-number", ownerLoginId = "someone-else"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(603L) } returns Optional.of(milestoneAttachmentBadId)

                mockMvc.perform(
                    post("/files/603")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("MILESTONE을 찾을 수 없으면 403 Forbidden을 반환한다") {
                val milestoneAttachment = Attachment(
                    id = 601L, name = "milestone-attach2.png", hash = "milestonehash2",
                    containerType = ResourceType.MILESTONE, containerId = "41", ownerLoginId = "someone-else"
                )
                every { milestoneRepository.findById(41L) } returns Optional.empty()
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(601L) } returns Optional.of(milestoneAttachment)

                mockMvc.perform(
                    post("/files/601")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("MILESTONE 수정 권한이 없으면 403 Forbidden을 반환한다") {
                val project = mockk<Project>()
                val milestone = mockk<Milestone>()
                val milestoneAttachment = Attachment(
                    id = 602L, name = "milestone-attach3.png", hash = "milestonehash3",
                    containerType = ResourceType.MILESTONE, containerId = "42", ownerLoginId = "someone-else"
                )
                every { milestone.project } returns project
                every { milestoneRepository.findById(42L) } returns Optional.of(milestone)
                every { accessControl.isAllowedToUpdateMilestone(loginUser, project) } returns false
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(602L) } returns Optional.of(milestoneAttachment)

                mockMvc.perform(
                    post("/files/602")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("REVIEW_COMMENT 첨부파일은 컨테이너 UPDATE 권한이 있으면 삭제할 수 있다") {
                val reviewAttachment = Attachment(
                    id = 700L, name = "review-attach.png", hash = "reviewhash",
                    containerType = ResourceType.REVIEW_COMMENT, containerId = "60", ownerLoginId = "someone-else"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(700L) } returns Optional.of(reviewAttachment)
                every {
                    accessControl.isAllowedAttachment(loginUser, reviewAttachment, Operation.UPDATE)
                } returns true
                every { attachmentService.delete(reviewAttachment) } returns Unit
                every { attachmentRepository.existsByHash("reviewhash") } returns false

                mockMvc.perform(
                    post("/files/700")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("COMMIT_COMMENT/REVIEW_COMMENT 컨테이너 UPDATE 권한이 없으면 403 Forbidden을 반환한다") {
                val reviewAttachment = Attachment(
                    id = 701L, name = "review-attach2.png", hash = "reviewhash2",
                    containerType = ResourceType.REVIEW_COMMENT, containerId = "61", ownerLoginId = "someone-else"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(701L) } returns Optional.of(reviewAttachment)
                every {
                    accessControl.isAllowedAttachment(loginUser, reviewAttachment, Operation.UPDATE)
                } returns false

                mockMvc.perform(
                    post("/files/701")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("정의되지 않은 그 외 리소스 타입(else 분기)은 업로더 본인이면 삭제할 수 있다") {
                val etcAttachment = Attachment(
                    id = 800L, name = "etc-attach.png", hash = "etchash",
                    containerType = ResourceType.WIKI_PAGE, containerId = "70", ownerLoginId = "tester"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(800L) } returns Optional.of(etcAttachment)
                every { attachmentService.delete(etcAttachment) } returns Unit
                every { attachmentRepository.existsByHash("etchash") } returns false

                mockMvc.perform(
                    post("/files/800")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("정의되지 않은 그 외 리소스 타입(else 분기)은 업로더 본인이 아니고 사이트관리자도 아니면 403을 반환한다") {
                val etcAttachment = Attachment(
                    id = 801L, name = "etc-attach2.png", hash = "etchash2",
                    containerType = ResourceType.WIKI_PAGE, containerId = "71", ownerLoginId = "someone-else"
                )
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(801L) } returns Optional.of(etcAttachment)

                mockMvc.perform(
                    post("/files/801")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("정의되지 않은 그 외 리소스 타입(else 분기)은 업로더 본인이 아니어도 사이트관리자면 삭제할 수 있다") {
                val siteAdmin = User(id = 2L, loginId = "admin", name = "관리자", state = UserState.SITE_ADMIN)
                val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")
                val etcAttachment = Attachment(
                    id = 802L, name = "etc-attach3.png", hash = "etchash3",
                    containerType = ResourceType.WIKI_PAGE, containerId = "72", ownerLoginId = "someone-else"
                )
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteAdmin)
                every { attachmentRepository.findById(802L) } returns Optional.of(etcAttachment)
                every { attachmentService.delete(etcAttachment) } returns Unit
                every { attachmentRepository.existsByHash("etchash3") } returns false

                mockMvc.perform(
                    post("/files/802")
                        .param("_method", "delete")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
            }

            it("첨부 삭제 후 원본 파일이 남아있으면 그 사실을 안내하는 메시지를 반환한다") {
                val project = mockk<Project>()
                val issue = mockk<Issue>()
                every { issue.project } returns project
                every { issue.authorLoginId } returns "tester"
                every { issueRepository.findById(10L) } returns Optional.of(issue)
                every { accessControl.isAllowedToUpdateIssue(loginUser, project, "tester") } returns true
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { attachmentService.delete(attachment) } returns Unit
                every { attachmentRepository.existsByHash("somehash123") } returns true

                mockMvc.perform(
                    post("/files/100")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(containsString("origin file still exists.")))
            }
        }

        describe("미커버 분기 테스트") {
            // getFile()의 Content-Type 헤더는 attachment.mimeType ?: "application/octet-stream" 인데,
            // 기존 테스트는 전부 mimeType이 있는 첨부(text/plain)만 다뤄서 null 쪽(기본값 대체)이 비어 있었다.
            it("mimeType이 없는 첨부파일을 조회하면 Content-Type을 application/octet-stream으로 기본 처리한다") {
                val noMimeAttachment = Attachment(
                    id = 900L, name = "no-mime.bin", hash = "nomimehash",
                    containerType = ResourceType.ISSUE_POST, containerId = "10", mimeType = null, ownerLoginId = "tester"
                )
                every { attachmentRepository.findById(900L) } returns Optional.of(noMimeAttachment)
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { accessControl.isAllowedAttachment(loginUser, noMimeAttachment, Operation.READ) } returns true

                val tempFile = File.createTempFile("yona-test-nomime", "bin")
                tempFile.writeText("binary")
                tempFile.deleteOnExit()
                every { attachmentService.getFile(noMimeAttachment) } returns tempFile

                mockMvc.perform(
                    get("/files/900")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(header().string("Content-Type", "application/octet-stream"))
            }

            it("[TASK-09] getFile에서 file이 존재하지 않는 경우 INTERNAL_SERVER_ERROR를 반환한다") {
                val attachment = Attachment(id = 100L, name = "test.txt", hash = "abc", containerType = ResourceType.ISSUE_POST, containerId = "1", ownerLoginId = "user1")
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { accessControl.isAllowedAttachment(any(), attachment, Operation.READ) } returns true
                val mockFile = mockk<File>()
                every { mockFile.exists() } returns false
                every { attachmentService.getFile(attachment) } returns mockFile
                
                mockMvc.perform(
                    get("/files/100")
                ).andExpect(status().isInternalServerError)
            }
        }
    }
})
