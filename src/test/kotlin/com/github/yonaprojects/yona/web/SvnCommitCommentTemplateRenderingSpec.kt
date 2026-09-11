package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitComment
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserIdent
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.tmatesoft.svn.core.SVNNodeKind
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.Instant

// P3-56: code/svnDiff.html의 댓글 기능이 legacy(commit/svnDiff.scala.html) 대비 축소된 즉석
// 구현이었다(마크다운 에디터/파일첨부/멘션/권한기반삭제/삭제확인모달/마크다운렌더링/아바타 전부
// 없음). Git 쪽 code/diff.html이 쓰는 공용 컴포넌트(site/layout::markdownEditor,
// common/uploadForm, common/commentDeleteModal)로 통일했는지 실제 렌더링 결과로 검증한다.
//
// 실제 svn 클라이언트/서버 없이 SVNKit 저수준 커밋 에디터로 로컬 저장소에 리비전을 하나 만든 뒤
// (SvnRepositorySpec과 동일한 기법), 실제 컨트롤러(CodeViewController.showCommit)를 MockMvc로
// 호출해 최종 HTML을 Jsoup으로 검사한다.
class SvnCommitCommentTemplateRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val repositoryService: RepositoryService,
    private val commitCommentRepository: CommitCommentRepository,
    private val attachmentRepository: AttachmentRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    private fun commitOneFile(repo: com.github.yonaprojects.yona.domain.vcs.PlayRepository): String {
        val svnURL = SVNURL.fromFile(repo.getDirectory())
        val svnRepository = SVNRepositoryFactory.create(svnURL)
        svnRepository.authenticationManager = BasicAuthenticationManager.newInstance("tester", CharArray(0))
        try {
            val editor = svnRepository.getCommitEditor("첫 커밋", null)
            editor.openRoot(-1)
            editor.addFile("a.txt", null, -1)
            editor.applyTextDelta("a.txt", null)
            val deltaGenerator = SVNDeltaGenerator()
            val checksum = deltaGenerator.sendDelta(
                "a.txt", ByteArrayInputStream("hello".toByteArray(StandardCharsets.UTF_8)), editor, true
            )
            editor.closeFile("a.txt", checksum)
            editor.closeDir()
            val info = editor.closeEdit()
            return info.newRevision.toString()
        } finally {
            svnRepository.closeSession()
        }
    }

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("P3-56: SVN 커밋 상세 화면의 댓글 기능이 Git 쪽과 동등한 컴포넌트를 써야 한다") {
            val author = userRepository.findByLoginId("svncmt-author").orElseGet {
                userRepository.save(User(loginId = "svncmt-author", name = "댓글작성자", email = "svncmt-author@yona.io"))
            }
            val manager = userRepository.findByLoginId("svncmt-manager").orElseGet {
                userRepository.save(User(loginId = "svncmt-manager", name = "매니저", email = "svncmt-manager@yona.io"))
            }
            val outsider = userRepository.findByLoginId("svncmt-outsider").orElseGet {
                userRepository.save(User(loginId = "svncmt-outsider", name = "외부인", email = "svncmt-outsider@yona.io"))
            }

            val roleManager = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
            }

            val project = projectRepository.findAll().find { it.name == "svncmt-proj" && it.owner == "svncmt-author" }
                ?: projectRepository.save(
                    Project(name = "svncmt-proj", owner = "svncmt-author", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
                )

            if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, manager.id!!)) {
                projectUserRepository.save(ProjectUser(project = project, user = manager, role = roleManager))
            }

            val repository = repositoryService.getRepository(project)
            if (!repository.getDirectory().exists()) {
                repository.create()
            }
            val commitId = if (repository.isEmpty()) commitOneFile(repository) else repository.getRefNames().let {
                // 이미 커밋이 있으면(재실행) 첫 리비전을 그대로 재사용.
                "1"
            }

            val comment = commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(project, commitId)
                .firstOrNull()
                ?: commitCommentRepository.save(
                    CommitComment(
                        project = project,
                        commitId = commitId,
                        contents = "**bold** comment",
                        author = UserIdent(author),
                        createdDate = Instant.now()
                    )
                )

            // 주의: AttachmentRepository.findByContainerTypeAndContainerId는 @Cacheable이라(운영
            // 코드는 AttachmentServiceImpl을 통해 저장/삭제 시 @CacheEvict로 무효화되지만, 이
            // 테스트는 그 서비스를 거치지 않고 리포지토리에 직접 저장한다) 그 캐시드 조회로
            // "존재 여부"를 먼저 확인하면 아직 없던 시점의 빈 결과가 캐시에 박제되어 저장 후에도
            // 계속 빈 리스트를 돌려준다 — findAll()로 우회해 캐시를 건드리지 않는다.
            val alreadyExists = attachmentRepository.findAll()
                .any { it.containerType == ResourceType.COMMIT_COMMENT && it.containerId == comment.id.toString() }
            if (!alreadyExists) {
                attachmentRepository.save(
                    Attachment(
                        name = "svn-comment-attach.txt",
                        mimeType = "text/plain",
                        size = 5L,
                        containerType = ResourceType.COMMIT_COMMENT,
                        containerId = comment.id.toString()
                    )
                )
            }

            fun managerDetails() = YonaUserDetails(
                id = manager.id!!,
                loginId = manager.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            fun outsiderDetails() = YonaUserDetails(
                id = outsider.id!!,
                loginId = outsider.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            it("댓글 본문이 마크다운으로 렌더링돼야 한다(원문 이스케이프 아님)") {
                val html = mockMvc.perform(get("/${project.owner}/${project.name}/commit/$commitId"))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                val body = doc.select("#comment-${comment.id} .comment-body.markdown-wrap").first()
                (body != null) shouldBe true
                body!!.select("strong").isEmpty() shouldBe false
            }

            it("새 댓글 작성 폼은 markdownEditor(yona-markdown-editor) + uploadForm 프래그먼트를 써야 한다") {
                val html = mockMvc.perform(
                    get("/${project.owner}/${project.name}/commit/$commitId")
                        .with(SecurityMockMvcRequestPostProcessors.user(managerDetails()))
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                val editor = doc.select("[data-toggle=markdown-editor] yona-markdown-editor")
                editor.size shouldBe 1
                editor.attr("name") shouldBe "contents"

                val upload = doc.select(".upload-wrap#upload")
                upload.size shouldBe 1
                upload.attr("data-resource-type") shouldBe "COMMIT_COMMENT"
            }

            it("삭제 확인 모달(common/commentDeleteModal)이 렌더링돼야 한다") {
                val html = mockMvc.perform(
                    get("/${project.owner}/${project.name}/commit/$commitId")
                        .with(SecurityMockMvcRequestPostProcessors.user(managerDetails()))
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select("#comment-delete-modal").size shouldBe 1
            }

            it("매니저는 본인이 작성하지 않은 SVN 커밋 댓글도 삭제 버튼(권한기반)이 보여야 한다") {
                val html = mockMvc.perform(
                    get("/${project.owner}/${project.name}/commit/$commitId")
                        .with(SecurityMockMvcRequestPostProcessors.user(managerDetails()))
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                val deleteBtn = doc.select("#comment-${comment.id} [data-toggle=comment-delete]")
                deleteBtn.size shouldBe 1
                deleteBtn.attr("data-request-uri") shouldBe "/comments/COMMIT_COMMENT/${comment.id}"
            }

            it("프로젝트 멤버가 아닌 외부인(작성자도 매니저도 아님)에게는 삭제 버튼이 보이면 안 된다") {
                val html = mockMvc.perform(
                    get("/${project.owner}/${project.name}/commit/$commitId")
                        .with(SecurityMockMvcRequestPostProcessors.user(outsiderDetails()))
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select("#comment-${comment.id} [data-toggle=comment-delete]").size shouldBe 0
            }

            it("댓글 작성자의 아바타/프로필 링크가 렌더링돼야 한다") {
                val html = mockMvc.perform(get("/${project.owner}/${project.name}/commit/$commitId"))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                val avatarLink = doc.select("#comment-${comment.id} .comment-avatar a[href='/user/${author.loginId}']")
                avatarLink.size shouldBe 1
            }

            it("댓글별 첨부파일 목록(.attachments[data-attachments])이 렌더링돼야 한다") {
                val html = mockMvc.perform(get("/${project.owner}/${project.name}/commit/$commitId"))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                val attachmentsDiv = doc.select("#comment-${comment.id} .attachments[data-attachments]")
                attachmentsDiv.size shouldBe 1
                attachmentsDiv.attr("data-attachments").contains("svn-comment-attach.txt") shouldBe true
            }
        }
    }
}
