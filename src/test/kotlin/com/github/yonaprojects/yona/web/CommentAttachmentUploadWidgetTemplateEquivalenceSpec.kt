package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
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

// v1.6(Twirl) 대조 결과 발견된 두 결함 검증(P3-49 후속 — board/view.html은 원래 티켓 검토 범위
// 밖이었다). issue/view.scala.html·board/view.scala.html은 새 댓글 작성 폼에
// common.fileUploader(type, null)를 쓰는데, 이 헬퍼는 항상 formId="upload"를 하드코딩해
// 넘긴다(app/views/common/fileUploader.scala.html) — yobi.Files.js의 _initFileUploader()가
// 전역 $("#upload")로 컨테이너를 찾으므로 이 id가 없으면 클릭 업로드/드롭존/textarea
// 드래그드롭/붙여넣기가 전부 조용히 죽는다. 포팅본은 이 헬퍼 대신 raw uploadForm 프래그먼트를
// formId=null로 직접 호출해 id 자체가 렌더링되지 않았다.
class CommentAttachmentUploadWidgetTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("v1.6 대조: 새 댓글 작성 폼의 첨부파일 업로더(#upload) 마크업 계약") {
            val owner = userRepository.findByLoginId("cmtupload-owner").orElseGet {
                userRepository.save(User(loginId = "cmtupload-owner", name = "댓글업로드소유자", email = "cmtupload-owner@yona.io"))
            }
            val member = userRepository.findByLoginId("cmtupload-member").orElseGet {
                userRepository.save(User(loginId = "cmtupload-member", name = "댓글업로드멤버", email = "cmtupload-member@yona.io"))
            }
            val roleMember = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }
            val project = projectRepository.findAll().find { it.name == "cmtupload-proj" && it.owner == "cmtupload-owner" }
                ?: projectRepository.save(
                    Project(
                        name = "cmtupload-proj",
                        owner = "cmtupload-owner",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = false,
                        vcs = "GIT"
                    )
                )
            if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, member.id!!)) {
                projectUserRepository.save(ProjectUser(project = project, user = member, role = roleMember))
            }
            val memberDetails = YonaUserDetails(
                id = member.id!!,
                loginId = member.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            val issue = issueRepository.findAll().find { it.project.id == project.id && it.title == "댓글업로드 이슈" }
                ?: issueRepository.save(
                    Issue(
                        title = "댓글업로드 이슈", body = "본문", project = project, number = 1L,
                        authorId = member.id, authorLoginId = member.loginId
                    )
                )

            val posting = postingRepository.findAll().find { it.project.id == project.id && it.title == "댓글업로드 게시글" }
                ?: postingRepository.save(Posting(title = "댓글업로드 게시글", body = "본문", project = project, number = 1L))

            it("issue/view 화면의 새 댓글 폼은 id=upload 업로더 컨테이너와 CommentAttachmentsUpdate.js 없이도 yobi.Files.js 훅을 갖춰야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/issue/${issue.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                doc.select("form#comment-form div#upload[data-resource-type=ISSUE_COMMENT]").size shouldBe 1
            }

            it("board/view 화면의 새 댓글 폼도 동일하게 id=upload 업로더 컨테이너를 가져야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/${project.owner}/${project.name}/post/${posting.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                doc.select("form#comment-form div#upload[data-resource-type=NONISSUE_COMMENT]").size shouldBe 1
            }

            it("issue/view 화면의 담당자 지정 스크립트는 <th:block> 원문이 그대로 노출되지 않고 실제 JS 호출로 렌더링돼야 한다") {
                val response = mockMvc.perform(
                    get("/${project.owner}/${project.name}/issue/${issue.number}")
                        .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                // <script> 태그 안이라 Jsoup의 HTML 파서가 관여하지 않으므로 원문(raw text)을
                // 직접 검사한다 — 리터럴 "<th:block"이 남아있으면 브라우저 JS 파싱이 깨진다.
                response.contains("<th:block") shouldBe false
                response.contains("yonaAssgineeModule(") shouldBe true

                // th:inline="javascript" 블록 주석으로 처리했다가 겪은 회귀(같은 스크립트 안의
                // 다른 인라인 표현식이 JSON 문자열로 재이스케이프되며 따옴표가 중복되고
                // 슬래시가 \/ 로 escape됨)가 재발하지 않는지, 해당 스크립트 블록 안에서만
                // 좁혀서 확인한다(페이지 다른 곳의 빈 value="" 속성 등과 섞이지 않도록).
                val callSite = response.substringAfter("yonaAssgineeModule(").substringBefore(");")
                callSite.contains("\"/-_-api/v1/owners/${project.owner}/projects/${project.name}/issues/${issue.number}/assignableUsers\"") shouldBe true
                callSite.contains("\"\"") shouldBe false
                callSite.contains("\\/") shouldBe false
            }

            it("권한이 없는 사용자에게는 담당자 지정 스크립트 자체가 렌더링되지 않아야 한다") {
                val outsider = userRepository.findByLoginId("cmtupload-outsider").orElseGet {
                    userRepository.save(User(loginId = "cmtupload-outsider", name = "댓글업로드외부인", email = "cmtupload-outsider@yona.io"))
                }
                val outsiderDetails = YonaUserDetails(
                    id = outsider.id!!,
                    loginId = outsider.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                val response = mockMvc.perform(
                    get("/${project.owner}/${project.name}/issue/${issue.number}")
                        .with(SecurityMockMvcRequestPostProcessors.user(outsiderDetails))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                response.contains("yonaAssgineeModule(") shouldBe false
            }
        }
    }
}
