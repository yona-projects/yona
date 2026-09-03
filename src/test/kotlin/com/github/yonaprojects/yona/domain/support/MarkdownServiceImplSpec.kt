package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.Commit
import com.github.yonaprojects.yona.domain.vcs.GitBranch
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.MessageSource
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.databind.node.ObjectNode
import java.io.File
import java.io.OutputStream
import java.util.Date
import java.util.Optional
import java.util.Locale
import java.util.TimeZone

class MarkdownServiceImplSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>(relaxed = true)
    val issueRepository = mockk<IssueRepository>(relaxed = true)
    val userRepository = mockk<UserRepository>(relaxed = true)
    val organizationRepository = mockk<OrganizationRepository>(relaxed = true)
    val repositoryService = mockk<RepositoryService>(relaxed = true)
    val messageSource = mockk<MessageSource>(relaxed = true)

    val autoLinkRenderer = AutoLinkRenderer(
        projectRepository,
        issueRepository,
        userRepository,
        organizationRepository,
        repositoryService,
        messageSource
    )

    val issueMarkdownProjectRepository = mockk<ProjectRepository>()
    val issueMarkdownIssueRepository = mockk<IssueRepository>()
    val issueMarkdownUserRepository = mockk<UserRepository>()
    val issueMarkdownAccessControl = mockk<AccessControl>()

    val markdownService = MarkdownServiceImpl(
        autoLinkRenderer, repositoryService,
        issueMarkdownProjectRepository, issueMarkdownIssueRepository,
        issueMarkdownUserRepository, issueMarkdownAccessControl, messageSource,
        hostname = "yona.example.com"
    )

    // yona utils/Markdown.java:132-159 transformIssueLink()/:161-211 extractIssueLink() 대응 [GL-utils_Markdown-010]
    // (P2-33). 본문에 순수 이슈 URL(예: https://호스트/owner/proj/issue/5)을 그대로 붙여넣으면(사용자가
    // 직접 [텍스트](url)로 감싸지 않은 raw link만 — commonmark AutolinkExtension이 이런 raw URL을
    // <a href="...">그대로의 URL 텍스트</a>로 자동 링크화해 두고, 이 함수가 그 앵커의 href==text인
    // 것만 골라 이슈 링크인지 검사한다) "#번호.제목" + 상태뱃지로 자동 변환한다. 이슈 READ 권한이
    // 없으면 원본 링크를 그대로 두고 그 시점에서 문서 전체 스캔을 중단한다(legacy 원본의 `break` 그대로
    // — 이후에 나오는 다른 이슈 링크는 검사되지 않는, 일견 버그처럼 보이지만 원본 그대로 재현해야 하는
    // 동작).
    describe("MarkdownServiceImpl 순수 이슈URL 자동 링크화 (P2-33)") {
        val project = Project(id = 1L, name = "proj", owner = "owner", vcs = "GIT")
        val openIssue = Issue(
            id = 10L, title = "버그 수정", project = project, number = 5L,
            state = State.OPEN, authorId = 1L
        )

        beforeTest {
            clearMocks(
                issueMarkdownProjectRepository, issueMarkdownIssueRepository,
                issueMarkdownUserRepository, issueMarkdownAccessControl,
                answers = false
            )
            every { issueMarkdownUserRepository.findByLoginId(any()) } returns Optional.empty()
        }

        it("호스트가 일치하는 순수 이슈 URL은 #번호.제목 + 상태뱃지로 바뀌어야 한다") {
            every { issueMarkdownProjectRepository.findByOwnerAndName("owner", "proj") } returns Optional.of(project)
            every { issueMarkdownIssueRepository.findByProjectAndNumber(project, 5L) } returns openIssue
            every { issueMarkdownAccessControl.isAllowed(null, project, openIssue, Operation.READ) } returns true
            every { messageSource.getMessage("issue.state.open", null, "open", any()) } returns "열림"

            val rendered = markdownService.render("https://yona.example.com/owner/proj/issue/5")

            rendered shouldContain "#5.버그 수정"
            rendered shouldContain "issueLink"
            rendered shouldContain "issue-state"
        }

        it("이슈 READ 권한이 없으면 원본 링크를 그대로 두어야 한다") {
            val privateIssue = Issue(id = 11L, title = "비공개 이슈", project = project, number = 6L, state = State.OPEN, authorId = 1L)
            every { issueMarkdownProjectRepository.findByOwnerAndName("owner", "proj") } returns Optional.of(project)
            every { issueMarkdownIssueRepository.findByProjectAndNumber(project, 6L) } returns privateIssue
            every { issueMarkdownAccessControl.isAllowed(null, project, privateIssue, Operation.READ) } returns false

            val rendered = markdownService.render("https://yona.example.com/owner/proj/issue/6")

            rendered shouldNotContain "issueLink"
        }

        it("[텍스트](url) 형태로 직접 감싼 링크는 링크텍스트==href가 아니라 변환 대상이 아니어야 한다") {
            val rendered = markdownService.render("[여기 참고](https://yona.example.com/owner/proj/issue/5-wrapped)")

            rendered shouldNotContain "issueLink"
        }

        it("이슈 경로가 아닌 일반 프로젝트 URL은 변환하지 않아야 한다") {
            val rendered = markdownService.render("https://yona.example.com/owner/proj-nolink")

            rendered shouldNotContain "issueLink"
        }
    }

    // yona utils/Markdown.java:103-130 checkReferrer() 대응 (P2-32). application.noreferrer가 [GL-utils_Markdown-009]
    // 켜져 있으면 이 사이트 호스트명으로 시작하지 않는 외부 링크에 rel="noreferrer"를 붙인다 —
    // 지금까지는 알림메일 후처리(NotificationMailBodyProcessor, P1-27)에만 있고 일반 마크다운
    // 렌더링(이슈/댓글/위키 본문 등 전체)엔 없었다.
    describe("MarkdownServiceImpl noreferrer 처리 (P2-32)") {
        val noreferrerEnabledService = MarkdownServiceImpl(
            autoLinkRenderer, repositoryService,
            issueMarkdownProjectRepository, issueMarkdownIssueRepository,
            issueMarkdownUserRepository, issueMarkdownAccessControl, messageSource,
            noreferrerEnabled = true, hostname = "yona.example.com"
        )
        val noreferrerDisabledService = MarkdownServiceImpl(
            autoLinkRenderer, repositoryService,
            issueMarkdownProjectRepository, issueMarkdownIssueRepository,
            issueMarkdownUserRepository, issueMarkdownAccessControl, messageSource,
            noreferrerEnabled = false, hostname = "yona.example.com"
        )

        it("application.noreferrer가 켜져 있으면 외부 링크에 rel=noreferrer가 붙어야 한다") {
            val rendered = noreferrerEnabledService.render("[외부링크](https://evil.example.org/x)")
            rendered shouldContain "noreferrer"
        }

        it("이 사이트 호스트명으로 시작하는 링크에는 rel=noreferrer를 붙이지 않아야 한다") {
            val rendered = noreferrerEnabledService.render("[내부링크](https://yona.example.com/owner/project)")
            rendered shouldNotContain "noreferrer"
        }

        it("application.noreferrer가 꺼져 있으면 외부 링크라도 rel=noreferrer를 붙이지 않아야 한다") {
            // 렌더 결과 캐시(MarkdownRenderCache, P2-43)가 source.hashCode()만 키로 쓰는 전역
            // 캐시라 다른 테스트와 같은 본문을 쓰면 캐시 충돌이 나므로 본문 텍스트를 다르게 둔다.
            val rendered = noreferrerDisabledService.render("[다른외부링크](https://evil.example.org/y)")
            rendered shouldNotContain "noreferrer"
        }

        it("상대경로(호스트 없는) 링크에는 rel=noreferrer를 붙이지 않아야 한다") {
            val rendered = noreferrerEnabledService.render("[상대경로](/owner/project/issue/1)")
            rendered shouldNotContain "noreferrer"
        }
    }

    describe("MarkdownServiceImpl & AutoLinkRenderer TDD 단축 링크 변환 검증") {
        val project = Project(id = 1L, name = "yobi", owner = "yobi", vcs = "GIT")

        it("멘션 @yobi 링크 변환 테스트") {
            val user = User(id = 2L, loginId = "yobi", name = "요비")
            every { userRepository.findByLoginId("yobi") } returns Optional.of(user)
            every { organizationRepository.findByName("yobi") } returns Optional.empty()

            val input = "Mention: @yobi"
            val output = markdownService.render(input, true, project)
            println("=== MENTION OUTPUT: $output ===")

            output.shouldContain("href=\"/user/yobi\"")
            output.shouldContain("class=\"no-text-decoration user-link\"")
            output.shouldContain("@요비")
        }

        // yona Markdown.java:333-336/342-344 render(source, project, breaks, lang) 대응 (P1-140) —
        // 다이제스트 메일 배치 스레드처럼 요청 컨텍스트가 없어 LocaleContextHolder로 언어를 알 수 없는
        // 상황에서도, 호출자가 명시한 lang으로 @멘션 표시 이름이 렌더링돼야 한다.
        it("lang을 명시하면 LocaleContextHolder와 무관하게 그 언어로 멘션 이름을 렌더링해야 한다") {
            val bilingualUser = User(id = 3L, loginId = "gildong", name = "홍길동", englishName = "Gildong Hong", lang = "ko-KR")
            every { userRepository.findByLoginId("gildong") } returns Optional.of(bilingualUser)
            every { organizationRepository.findByName("gildong") } returns Optional.empty()

            val outputEn = markdownService.render("Mention: @gildong", true, project, "en")
            val outputKo = markdownService.render("Mention: @gildong", true, project, "ko")

            outputEn.shouldContain("@Gildong Hong")
            outputEn.shouldNotContain("@홍길동")
            outputKo.shouldContain("@홍길동")
            outputKo.shouldNotContain("@Gildong Hong")
        }

        it("이슈 #2 링크 변환 테스트") {
            val issue = Issue(id = 10L, title = "버그수정", project = project, number = 2L, state = State.OPEN)
            every { issueRepository.findByProjectAndNumber(any(), 2L) } returns issue
            every { messageSource.getMessage("issue.state.open", null, "open", any<Locale>()) } returns "열림"

            val input = "Issue no: #2"
            val output = markdownService.render(input, true, project)
            println("=== ISSUE OUTPUT: $output ===")

            output.shouldContain("href=\"/yobi/yobi/issue/2\"")
            output.shouldContain("class=\"issueLink\"")
            output.shouldContain("#2.버그수정")
            output.shouldContain("class=\"issue-state open\"")
            output.shouldContain("열림")
        }

        it("커밋 @763575 링크 변환 테스트") {
            val commit = object : Commit() {
                override fun getShortId(): String = "763575f"
                override fun getId(): String = "763575f177a4ce8b9370954de3ea1a1410205593"
                override fun getShortMessage(): String = ""
                override fun getMessage(): String? = null
                override fun getAuthor(): User? = null
                override fun getAuthorName(): String? = null
                override fun getAuthorEmail(): String? = null
                override fun getAuthorDate(): Date? = null
                override fun getAuthorTimezone(): TimeZone? = null
                override fun getCommitterName(): String? = null
                override fun getCommitterEmail(): String? = null
                override fun getCommitterDate(): Date? = null
                override fun getCommitterTimezone(): TimeZone? = null
                override fun getParentCount(): Int = 0
            }

            val playRepo = object : PlayRepository {
                override fun create() {}
                override fun isIntermediateFolder(path: String): Boolean = false
                override fun getMetaDataFromPath(path: String): ObjectNode? = null
                override fun getMetaDataFromPath(branch: String, path: String): ObjectNode? = null
                override fun getRawFile(revision: String, path: String): ByteArray = ByteArray(0)
                override fun delete() {}
                override fun getPatch(commitId: String): String = ""
                override fun getPatch(revA: String, revB: String): String = ""
                override fun getDiff(commitId: String): List<Any> = emptyList()
                override fun getDiff(revA: String, revB: String): List<Any> = emptyList()
                override fun getHistory(pageNum: Int, pageSize: Int, untilRev: String?, path: String?): List<Commit> = emptyList()
                override fun getCommit(rev: String): Commit? {
                    return if (rev == "763575f") commit else null
                }
                override fun getRefNames(): List<String> = emptyList()
                override fun isFile(path: String): Boolean = false
                override fun isFile(path: String, revStr: String): Boolean = false
                override fun renameTo(projectName: String): Boolean = true
                override fun getDefaultBranch(): String = "main"
                override fun setDefaultBranch(target: String) {}
                override fun getBranches(): List<GitBranch> = emptyList()
                override fun getHeadBranch(): GitBranch? = null
                override fun deleteBranch(branchName: String) {}
                override fun createBranch(branchName: String, startPoint: String) {}
                override fun getParentCommitOf(commitId: String): Commit? = null
                override fun isEmpty(): Boolean = false
                override fun move(srcProjectOwner: String, srcProjectName: String, destProjectOwner: String, destProjectName: String): Boolean = true
                override fun getDirectory(): File = File("/tmp")
                override fun getArchive(os: OutputStream, branchName: String) {}
                override fun getBlobId(revision: String, path: String): String? = null
            }

            every { repositoryService.getRepository(any()) } returns playRepo

            val input = "commit: @763575f"
            val output = markdownService.render(input, true, project)
            println("=== COMMIT OUTPUT: $output ===")

            output.shouldContain("href=\"/yobi/yobi/commit/763575f177a4ce8b9370954de3ea1a1410205593\"")
            output.shouldContain("763575f")
        }
    }

    describe("MarkdownServiceImpl 새니타이저 XSS 방지 검증 (allowlist 기반)") {
        it("<script> 태그는 완전히 제거되어야 한다") {
            val output = markdownService.render("본문 <script>alert('xss')</script> 끝")
            output.shouldNotContain("<script")
            output.shouldNotContain("alert(")
        }

        it("onclick 같은 이벤트 핸들러 속성은 허용목록에 없으므로 제거되어야 한다") {
            val output = markdownService.render("<div onclick=\"alert(1)\">내용</div>")
            output.shouldNotContain("onclick")
        }

        it("onerror 이벤트 핸들러가 있는 img 태그는 속성만 제거되고 태그는 허용되어야 한다") {
            val output = markdownService.render("<img src=\"x.png\" onerror=\"alert(1)\">")
            output.shouldNotContain("onerror")
        }

        it("javascript: 프로토콜 링크는 무해화되어야 한다") {
            val output = markdownService.render("[클릭](javascript:alert(1))")
            output.shouldNotContain("javascript:")
        }

        it("허용되지 않은 svg/onload 벡터는 태그 자체가 제거되어야 한다") {
            val output = markdownService.render("<svg onload=\"alert(1)\"></svg>")
            output.shouldNotContain("onload")
            output.shouldNotContain("<svg")
        }

        it("data: URI 기반 스크립트 삽입은 무해화되어야 한다") {
            val output = markdownService.render("<a href=\"data:text/html,<script>alert(1)</script>\">링크</a>")
            output.shouldNotContain("<script")
        }

        it("정상적인 GFM 표(table)는 그대로 렌더링되어야 한다") {
            val input = """
                |a|b|
                |---|---|
                |1|2|
            """.trimIndent()
            val output = markdownService.render(input)
            output.shouldContain("<table")
            output.shouldContain("<td>1</td>")
        }

        it("펜스 코드블록은 그대로 렌더링되어야 한다") {
            val output = markdownService.render("```\nval x = 1\n```")
            output.shouldContain("<pre")
            output.shouldContain("val x = 1")
        }
    }

    // yona Markdown.java:346-356 renderFileInCodeBrowser()/renderFileInReadme() 대응 (P1-139). [GL-utils_Markdown-017;GL-utils_Markdown-018]
    describe("renderFileInCodeBrowser / renderFileInReadme - 상대경로 링크 치환") {
        val project = Project(id = 1L, name = "yobi", owner = "yobi", vcs = "GIT")
        val playRepoWithMain = object : PlayRepository by mockk<PlayRepository>(relaxed = true) {
            override fun getDefaultBranch(): String = "refs/heads/main"
        }

        it("renderFileInCodeBrowser는 상대 이미지 링크를 files 경로 절대링크로 바꿔 렌더링해야 한다") {
            every { repositoryService.getRepository(project) } returns playRepoWithMain

            val output = markdownService.renderFileInCodeBrowser("![스크린샷](./images/shot.png)", project)

            output.shouldContain("/yobi/yobi/files/main/images/shot.png")
        }

        it("renderFileInCodeBrowser는 절대/외부 링크는 건드리지 않아야 한다") {
            every { repositoryService.getRepository(project) } returns playRepoWithMain

            val output = markdownService.renderFileInCodeBrowser("![로고](https://example.com/logo.png)", project)

            output.shouldContain("https://example.com/logo.png")
        }

        it("renderFileInReadme는 상대 일반 링크를 code 경로 절대링크로, 이미지 링크는 files 경로로 바꿔야 한다") {
            every { repositoryService.getRepository(project) } returns playRepoWithMain

            val output = markdownService.renderFileInReadme(
                "See [docs](./docs/guide.md) and ![logo](./images/logo.png)", project
            )

            output.shouldContain("/yobi/yobi/code/main/docs/guide.md")
            output.shouldContain("/yobi/yobi/files/main/images/logo.png")
        }

        // yona utils/Markdown.java:218-270 renderWithHighlight()의 CacheStore.renderedMarkdown
        // 캐시 대응 (P2-43, 사용자 지시로 원본 구조 그대로 포팅).
        describe("렌더 결과 캐시 (P2-43)") {
            it("같은 source를 같은 breaks로 반복 렌더링하면 매번 동일한 결과를 반환해야 한다") {
                val source = "# cache-basic-test\n\nsome **bold** text"

                val first = markdownService.render(source, true)
                val second = markdownService.render(source, true)

                second shouldBe first
                first.shouldContain("<strong>bold</strong>")
            }

            // yona 원본 캐시 키가 source.hashCode()만 쓰고 breaks는 키에 포함하지 않아, 같은
            // source를 breaks 값만 바꿔 렌더링하면 캐시된 이전 breaks 결과가 그대로 반환된다 —
            // 이 특성을 구조 그대로 포팅했으므로 yona에서도 동일하게 재현돼야 한다.
            it("동일 source를 breaks만 바꿔 렌더링해도 캐시된 이전 breaks 결과가 그대로 반환된다 (yona 원본 캐시 키 특성 그대로 포팅)") {
                val source = "cache-quirk-test-line-one\nline-two"

                val renderedWithBreaksTrue = markdownService.render(source, true)
                renderedWithBreaksTrue.shouldContain("<br>")

                val renderedWithBreaksFalse = markdownService.render(source, false)

                renderedWithBreaksFalse shouldBe renderedWithBreaksTrue
                renderedWithBreaksFalse.shouldContain("<br>")
            }
        }
    }

    describe("MarkdownServiceImpl 추가 커버리지 검증") {
        val project = Project(id = 1L, name = "yobi", owner = "yobi", vcs = "GIT")
        
        it("body가 비어있으면 빈 문자열을 반환해야 한다") {
            markdownService.render("") shouldBe ""
            markdownService.render("", breaks = true) shouldBe ""
            markdownService.render("", breaks = true, project = null) shouldBe ""
        }

        it("checkReferrer - host가 null인 경우(mailto 등) 예외/오류 없이 처리된다") {
            val service = MarkdownServiceImpl(
                autoLinkRenderer, repositoryService,
                issueMarkdownProjectRepository, issueMarkdownIssueRepository,
                issueMarkdownUserRepository, issueMarkdownAccessControl, messageSource,
                noreferrerEnabled = true, hostname = "yona.example.com"
            )
            val rendered = service.render("<a href=\"mailto:test@test.com\">메일</a>")
            rendered.shouldContain("mailto:test@test.com")
        }

        it("checkReferrer - URISyntaxException이 발생하는 잘못된 링크는 무시된다") {
            val service = MarkdownServiceImpl(
                autoLinkRenderer, repositoryService,
                issueMarkdownProjectRepository, issueMarkdownIssueRepository,
                issueMarkdownUserRepository, issueMarkdownAccessControl, messageSource,
                noreferrerEnabled = true, hostname = "yona.example.com"
            )
            val rendered = service.render("<a href=\"http://[\">Bad Link</a>")
            rendered.shouldContain("http://[")
        }

        it("transformIssueLink - URI path가 null이거나 issue 패턴이 없으면 변환하지 않는다") {
            val rendered1 = markdownService.render("https://yona.example.com")
            rendered1.shouldContain("https://yona.example.com")

            val rendered2 = markdownService.render("https://yona.example.com/owner/proj/other/5")
            rendered2.shouldContain("other/5")
        }

        it("transformIssueLink - project 또는 issue를 못 찾으면 변환하지 않는다") {
            every { issueMarkdownProjectRepository.findByOwnerAndName("owner", "unknown") } returns Optional.empty()
            val rendered1 = markdownService.render("https://yona.example.com/owner/unknown/issue/5")
            rendered1.shouldNotContain("issueLink")

            val proj = Project(id = 1L, name = "proj", owner = "owner", vcs = "GIT")
            every { issueMarkdownProjectRepository.findByOwnerAndName("owner", "proj") } returns Optional.of(proj)
            every { issueMarkdownIssueRepository.findByProjectAndNumber(proj, 999L) } returns null
            val rendered2 = markdownService.render("https://yona.example.com/owner/proj/issue/999")
            rendered2.shouldNotContain("issueLink")
        }

        it("transformIssueLink - RuntimeException 발생 시 로깅하고 넘어간다") {
            every { issueMarkdownProjectRepository.findByOwnerAndName("owner", "error") } throws RuntimeException("DB error")
            val rendered = markdownService.render("https://yona.example.com/owner/error/issue/1")
            rendered.shouldContain("https://yona.example.com/owner/error/issue/1")
            rendered.shouldNotContain("issueLink")
        }

        it("transformIssueLink - fragment가 있으면 링크 텍스트에 포함된다") {
            val proj = Project(id = 1L, name = "proj", owner = "owner", vcs = "GIT")
            val issue = Issue(id = 10L, title = "프래그먼트", project = proj, number = 7L, state = State.OPEN)
            every { issueMarkdownProjectRepository.findByOwnerAndName("owner", "proj") } returns Optional.of(proj)
            every { issueMarkdownIssueRepository.findByProjectAndNumber(proj, 7L) } returns issue
            every { issueMarkdownAccessControl.isAllowed(null, proj, issue, Operation.READ) } returns true
            every { messageSource.getMessage(any(), any(), any(), any()) } returns "열림"

            val rendered = markdownService.render("https://yona.example.com/owner/proj/issue/7#comment-123")
            rendered.shouldContain("#7.프래그먼트#comment-123")
        }

        it("getDefaultBranch 예외 시 master 반환") {
            every { repositoryService.getRepository(any()) } throws RuntimeException("Repo not found")
            val output = markdownService.renderFileInCodeBrowser("![test](./img.png)", project)
            output.shouldContain("/yobi/yobi/files/master/img.png")
        }

        it("hostname 인자를 생략하면 기본값 localhost가 적용된다") {
            val service = MarkdownServiceImpl(
                autoLinkRenderer, repositoryService,
                issueMarkdownProjectRepository, issueMarkdownIssueRepository,
                issueMarkdownUserRepository, issueMarkdownAccessControl, messageSource
            )
            val rendered = service.render("https://localhost/owner/proj/other/5")
            rendered.shouldContain("other/5")
        }

        it("issue.state 메시지 코드가 해석되지 않아 null이 반환되면 stateStr로 대체해야 한다") {
            val proj = Project(id = 1L, name = "proj", owner = "owner", vcs = "GIT")
            val issue = Issue(id = 30L, title = "메시지없음", project = proj, number = 11L, state = State.OPEN)
            every { issueMarkdownProjectRepository.findByOwnerAndName("owner", "proj") } returns Optional.of(proj)
            every { issueMarkdownIssueRepository.findByProjectAndNumber(proj, 11L) } returns issue
            every { issueMarkdownAccessControl.isAllowed(null, proj, issue, Operation.READ) } returns true
            every { messageSource.getMessage(any(), any(), any(), any()) } returns null

            val rendered = markdownService.render("https://yona.example.com/owner/proj/issue/11")

            rendered.shouldContain("#11.메시지없음")
        }

        // resolveCurrentUser()의 SecurityContextHolder 인증 분기 — 기존 테스트는 전부 컨텍스트에
        // 인증 정보를 설정하지 않아 `authentication == null` 쪽만 탔다. 인증됨+비익명(실제 사용자
        // 조회) 및 AnonymousAuthenticationToken 두 분기가 비어 있었다.
        describe("resolveCurrentUser - SecurityContextHolder 인증 분기") {
            beforeTest {
                clearMocks(
                    issueMarkdownProjectRepository, issueMarkdownIssueRepository,
                    issueMarkdownUserRepository, issueMarkdownAccessControl,
                    answers = false
                )
            }
            afterTest {
                SecurityContextHolder.clearContext()
            }

            it("인증되고 익명이 아니면 userRepository로 조회한 사용자로 권한을 검사해야 한다") {
                val proj = Project(id = 1L, name = "proj", owner = "owner", vcs = "GIT")
                val issue = Issue(id = 20L, title = "인증사용자테스트", project = proj, number = 8L, state = State.OPEN)
                val currentUser = User(id = 99L, loginId = "loginuser", name = "로그인유저")
                every { issueMarkdownProjectRepository.findByOwnerAndName("owner", "proj") } returns Optional.of(proj)
                every { issueMarkdownIssueRepository.findByProjectAndNumber(proj, 8L) } returns issue
                every { issueMarkdownUserRepository.findByLoginId("loginuser") } returns Optional.of(currentUser)
                every { issueMarkdownAccessControl.isAllowed(currentUser, proj, issue, Operation.READ) } returns true
                every { messageSource.getMessage(any(), any(), any(), any()) } returns "열림"

                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken("loginuser", "password", listOf(SimpleGrantedAuthority("ROLE_USER")))

                val rendered = markdownService.render("https://yona.example.com/owner/proj/issue/8")

                rendered.shouldContain("#8.인증사용자테스트")
                io.mockk.verify { issueMarkdownAccessControl.isAllowed(currentUser, proj, issue, Operation.READ) }
            }

            it("AnonymousAuthenticationToken이면 익명 사용자(null)로 권한을 검사해야 한다") {
                val proj = Project(id = 1L, name = "proj", owner = "owner", vcs = "GIT")
                val issue = Issue(id = 21L, title = "익명토큰테스트", project = proj, number = 9L, state = State.OPEN)
                every { issueMarkdownProjectRepository.findByOwnerAndName("owner", "proj") } returns Optional.of(proj)
                every { issueMarkdownIssueRepository.findByProjectAndNumber(proj, 9L) } returns issue
                every { issueMarkdownAccessControl.isAllowed(null, proj, issue, Operation.READ) } returns true
                every { messageSource.getMessage(any(), any(), any(), any()) } returns "열림"

                SecurityContextHolder.getContext().authentication =
                    AnonymousAuthenticationToken("key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")))

                val rendered = markdownService.render("https://yona.example.com/owner/proj/issue/9")

                rendered.shouldContain("#9.익명토큰테스트")
                io.mockk.verify(exactly = 0) { issueMarkdownUserRepository.findByLoginId(any()) }
            }

            it("인증되었으나 isAuthenticated=false이면 익명 사용자(null)로 권한을 검사해야 한다") {
                val proj = Project(id = 1L, name = "proj", owner = "owner", vcs = "GIT")
                val issue = Issue(id = 22L, title = "미인증테스트", project = proj, number = 10L, state = State.OPEN)
                every { issueMarkdownProjectRepository.findByOwnerAndName("owner", "proj") } returns Optional.of(proj)
                every { issueMarkdownIssueRepository.findByProjectAndNumber(proj, 10L) } returns issue
                every { issueMarkdownAccessControl.isAllowed(null, proj, issue, Operation.READ) } returns true
                every { messageSource.getMessage(any(), any(), any(), any()) } returns "열림"

                val notAuthenticated = UsernamePasswordAuthenticationToken("loginuser", "password")
                notAuthenticated.isAuthenticated shouldBe false
                SecurityContextHolder.getContext().authentication = notAuthenticated

                val rendered = markdownService.render("https://yona.example.com/owner/proj/issue/10")

                rendered.shouldContain("#10.미인증테스트")
                io.mockk.verify(exactly = 0) { issueMarkdownUserRepository.findByLoginId(any()) }
            }
        }
    }
})
