package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.Commit
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.MessageSource
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.user.User

// yona utils/AutoLinkRenderer.java:268-296 toValidSHALink() 대응 (P2-35). SHA 커밋 자동 링크화가 [GL-utils_AutoLinkRenderer-022;GL-utils_AutoLinkRenderer-023]
// project.isGit() 뿐 아니라 project.isCodeAvailable()(코드브라우저 메뉴 활성 여부)도 검사해야 하는데,
// yona는 vcs=="GIT" 검사만 있고 project.isCodeEnabled 검사가 빠져 있었다 — 코드브라우저를 끈
// 프로젝트에서도 커밋 SHA가 링크로 렌더링되는(존재하지 않는 코드브라우저 URL을 가리키는) 문제.
class AutoLinkRendererSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val issueRepository = mockk<IssueRepository>()
    val userRepository = mockk<UserRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val repositoryService = mockk<RepositoryService>()
    val messageSource = mockk<MessageSource>()

    val autoLinkRenderer = AutoLinkRenderer(
        projectRepository, issueRepository, userRepository, organizationRepository,
        repositoryService, messageSource
    )

    val sha = "abc1234"

    beforeTest {
        clearMocks(projectRepository, issueRepository, userRepository, organizationRepository, repositoryService, messageSource)
    }

    describe("render() - SHA 자동 링크화 (P2-35)") {
        it("코드브라우저가 켜진(isCodeEnabled=true) GIT 프로젝트에서는 SHA가 커밋 링크로 변환된다") {
            val project = Project(id = 1L, owner = "owner", name = "proj", vcs = "GIT", isCodeEnabled = true)
            val repo = mockk<PlayRepository>()
            val commit = mockk<Commit>()
            every { repositoryService.getRepository(project) } returns repo
            every { repo.getCommit(sha) } returns commit
            every { commit.getId() } returns sha
            every { commit.getShortId() } returns sha

            val result = autoLinkRenderer.render(sha, project)

            result shouldContain "<a"
            result shouldContain "/owner/proj/commit/$sha"
        }

        it("코드브라우저가 꺼진(isCodeEnabled=false) 프로젝트에서는 GIT이어도 SHA를 링크로 바꾸지 않는다") {
            val project = Project(id = 2L, owner = "owner", name = "proj2", vcs = "GIT", isCodeEnabled = false)

            val result = autoLinkRenderer.render(sha, project)

            result shouldNotContain "<a"
            verify(exactly = 0) { repositoryService.getRepository(any()) }
        }
    }

    describe("render() - SHA 자동 링크화 추가 분기") {
        it("vcs가 null이면 GIT으로 간주해 커밋 링크로 변환된다") {
            val project = Project(id = 10L, owner = "o", name = "p10", vcs = null, isCodeEnabled = true)
            val repo = mockk<PlayRepository>()
            val commit = mockk<Commit>()
            every { repositoryService.getRepository(project) } returns repo
            every { repo.getCommit(sha) } returns commit
            every { commit.getId() } returns sha
            every { commit.getShortId() } returns sha

            val result = autoLinkRenderer.render(sha, project)

            result shouldContain "<a"
        }

        it("vcs가 GIT이 아니면(SVN) 코드브라우저가 켜져 있어도 링크로 바꾸지 않는다") {
            val project = Project(id = 11L, owner = "o", name = "p11", vcs = "SVN", isCodeEnabled = true)

            val result = autoLinkRenderer.render(sha, project)

            result shouldNotContain "<a"
            verify(exactly = 0) { repositoryService.getRepository(any()) }
        }

        it("해당 SHA의 커밋이 저장소에 없으면 링크로 바꾸지 않는다") {
            val project = Project(id = 12L, owner = "o", name = "p12", vcs = "GIT", isCodeEnabled = true)
            val repo = mockk<PlayRepository>()
            every { repositoryService.getRepository(project) } returns repo
            every { repo.getCommit(sha) } returns null

            val result = autoLinkRenderer.render(sha, project)

            result shouldNotContain "<a"
        }

        it("저장소 조회 중 예외가 발생하면 링크로 바꾸지 않고 무시한다") {
            val project = Project(id = 13L, owner = "o", name = "p13", vcs = "GIT", isCodeEnabled = true)
            every { repositoryService.getRepository(project) } throws IllegalStateException("boom")

            val result = autoLinkRenderer.render(sha, project)

            result shouldNotContain "<a"
        }

        it("path@SHA 형태면 path로 프로젝트를 찾아 SHA 앞에 접두어로 붙인다") {
            val currentProject = Project(id = 14L, owner = "o", name = "cur14", vcs = "GIT", isCodeEnabled = true)
            val otherProject = Project(id = 15L, owner = "o", name = "other15", vcs = "GIT", isCodeEnabled = true)
            every { projectRepository.findByOwnerAndName("other15", "cur14") } returns Optional.of(otherProject)
            val repo = mockk<PlayRepository>()
            val commit = mockk<Commit>()
            every { repositoryService.getRepository(otherProject) } returns repo
            every { repo.getCommit(sha) } returns commit
            every { commit.getId() } returns sha
            every { commit.getShortId() } returns sha

            val result = autoLinkRenderer.render("other15@$sha", currentProject)

            result shouldContain "other15@$sha"
        }
    }

    describe("render() - 이슈 자동 링크화") {
        it("경로 없이 #123만 있고 currentProject가 있으면 이슈 링크로 변환된다") {
            val project = Project(id = 20L, owner = "owner", name = "proj20")
            val issue = Issue(title = "버그 수정", project = project, number = 123L, state = State.OPEN)
            every { issueRepository.findByProjectAndNumber(project, 123L) } returns issue
            every { messageSource.getMessage(any(), null, any(), any()) } returns "열림"

            val result = autoLinkRenderer.render("#123", project)

            result shouldContain "<a"
            result shouldContain "/owner/proj20/issue/123"
        }

        it("path#123 형태면 슬래시 포함 경로로 프로젝트를 찾아 이슈 링크의 접두어로 붙인다") {
            val currentProject = Project(id = 21L, owner = "owner", name = "cur21")
            val otherProject = Project(id = 22L, owner = "owner", name = "other22")
            val issue = Issue(title = "제목", project = otherProject, number = 5L, state = State.OPEN)
            every { projectRepository.findByOwnerAndName("owner", "other22") } returns Optional.of(otherProject)
            every { issueRepository.findByProjectAndNumber(otherProject, 5L) } returns issue
            every { messageSource.getMessage(any(), null, any(), any()) } returns "열림"

            val result = autoLinkRenderer.render("owner/other22#5", currentProject)

            result shouldContain "/owner/other22/issue/5"
            result shouldContain "owner/other22#5"
        }

        it("슬래시 없는 path#123 형태면 currentProject의 이름과 조합해 프로젝트를 찾는다") {
            val currentProject = Project(id = 23L, owner = "owner", name = "cur23")
            val teamProject = Project(id = 24L, owner = "team1", name = "cur23")
            val issue = Issue(title = "제목2", project = teamProject, number = 7L, state = State.OPEN)
            every { projectRepository.findByOwnerAndName("team1", "cur23") } returns Optional.of(teamProject)
            every { issueRepository.findByProjectAndNumber(teamProject, 7L) } returns issue
            every { messageSource.getMessage(any(), null, any(), any()) } returns "열림"

            val result = autoLinkRenderer.render("team1#7", currentProject)

            result shouldContain "/team1/cur23/issue/7"
        }

        it("currentProject가 없으면(null) #123은 링크로 변환되지 않는다") {
            val result = autoLinkRenderer.render("#123", null)

            result shouldNotContain "<a"
        }

        it("currentProject가 없고 path에 슬래시도 없으면 리포지토리 조회 없이 링크로 변환되지 않는다") {
            val result = autoLinkRenderer.render("otherteam#5", null)

            result shouldNotContain "<a"
            verify(exactly = 0) { projectRepository.findByOwnerAndName(any(), any()) }
        }

        it("project는 찾았지만 해당 번호의 이슈가 없으면 링크로 변환되지 않는다") {
            val project = Project(id = 25L, owner = "owner", name = "proj25")
            every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

            val result = autoLinkRenderer.render("#999", project)

            result shouldNotContain "<a"
        }

        it("이슈 번호가 Long 범위를 초과하면 링크로 변환되지 않는다") {
            val project = Project(id = 26L, owner = "owner", name = "proj26")
            every { projectRepository.findByOwnerAndName(any(), any()) } returns Optional.empty()

            val result = autoLinkRenderer.render("#99999999999999999999", project)

            result shouldNotContain "<a"
            verify(exactly = 0) { issueRepository.findByProjectAndNumber(any(), any()) }
        }
    }

    describe("render() - 사용자/조직/프로젝트 링크") {
        it("@이름이 조직명과 일치하면 조직 링크로 변환된다") {
            val org = mockk<Organization>()
            every { org.name } returns "myorg"
            every { organizationRepository.findByName("myorg") } returns Optional.of(org)
            every { userRepository.findByLoginId("myorg") } returns Optional.empty()

            val result = autoLinkRenderer.render("@myorg", null)

            result shouldContain "org-link"
            result shouldContain "/org/myorg"
        }

        it("조직이 아니지만 사용자가 있으면 사용자 링크로 변환된다(기본 아바타)") {
            val user = User(id = 30L, name = "홍길동", loginId = "gildong")
            every { organizationRepository.findByName("gildong") } returns Optional.empty()
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)

            val result = autoLinkRenderer.render("@gildong", null, "ko")

            result shouldContain "user-link"
            result shouldContain "/user/gildong"
            result shouldNotContain "<img"
        }

        it("사용자가 커스텀 아바타를 가지고 있으면 img 태그가 포함된다") {
            val user = User(id = 31L, name = "김철수", loginId = "chulsoo")
            user.avatarId = 999L
            every { organizationRepository.findByName("chulsoo") } returns Optional.empty()
            every { userRepository.findByLoginId("chulsoo") } returns Optional.of(user)

            val result = autoLinkRenderer.render("@chulsoo", null, "ko")

            // jsoup 1.21.1(1.17.2에서 업그레이드)부터 data-content 속성값 안의 `<img`를 정확히
            // `&lt;img`로 이스케이프해 직렬화한다(구버전은 속성값 안 `<`를 그대로 남기는 관대한
            // 동작이었음 — 실측으로 확인, HTML 표준상으로는 어느 쪽이든 브라우저가 속성값을 파싱해
            // DOM에 저장할 때 디코딩되므로 Bootstrap popover 동작은 동일하다). 이스케이프된 형태로
            // 검증하도록 갱신.
            result shouldContain "&lt;img"
            result shouldContain "/files/999"
        }

        it("lang 인자가 없으면 현재 요청 로케일을 사용해도 정상적으로 링크가 만들어진다") {
            val user = User(id = 32L, name = "이영희", loginId = "younghee")
            every { organizationRepository.findByName("younghee") } returns Optional.empty()
            every { userRepository.findByLoginId("younghee") } returns Optional.of(user)

            val result = autoLinkRenderer.render("@younghee", null)

            result shouldContain "user-link"
        }

        it("사용자 id가 없으면(비영속 상태) 링크로 변환되지 않는다") {
            val user = User(id = null, name = "유령", loginId = "ghost")
            every { organizationRepository.findByName("ghost") } returns Optional.empty()
            every { userRepository.findByLoginId("ghost") } returns Optional.of(user)

            val result = autoLinkRenderer.render("@ghost", null)

            result shouldNotContain "<a"
        }

        it("loginId가 anonymous면 링크로 변환되지 않는다") {
            val user = User(id = 33L, name = "익명", loginId = "anonymous")
            every { organizationRepository.findByName("anonymous") } returns Optional.empty()
            every { userRepository.findByLoginId("anonymous") } returns Optional.of(user)

            val result = autoLinkRenderer.render("@anonymous", null)

            result shouldNotContain "<a"
        }

        it("조직도 사용자도 없으면 링크로 변환되지 않는다") {
            every { organizationRepository.findByName("nobody") } returns Optional.empty()
            every { userRepository.findByLoginId("nobody") } returns Optional.empty()

            val result = autoLinkRenderer.render("@nobody", null)

            result shouldNotContain "<a"
        }

        it("@owner/project 형태면 프로젝트 링크로 변환된다") {
            val project = Project(id = 40L, owner = "acme", name = "widget")
            every { projectRepository.findByOwnerAndName("acme", "widget") } returns Optional.of(project)

            val result = autoLinkRenderer.render("@acme/widget", null)

            result shouldContain "project-link"
            result shouldContain "/acme/widget"
        }

        it("@owner/project 형태인데 프로젝트가 없으면 링크로 변환되지 않는다") {
            every { projectRepository.findByOwnerAndName("acme", "missing") } returns Optional.empty()

            val result = autoLinkRenderer.render("@acme/missing", null)

            result shouldNotContain "<a"
        }
    }

    describe("render() - 단어 경계 판정(isWrappedNonCharacter)") {
        it("앞뒤로 단어문자가 없는 단독 #123은 변환된다") {
            val project = Project(id = 50L, owner = "owner", name = "proj50")
            val issue = Issue(title = "제목", project = project, number = 1L, state = State.OPEN)
            every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
            every { messageSource.getMessage(any(), null, any(), any()) } returns "열림"

            val result = autoLinkRenderer.render("보세요 #1 입니다", project)

            result shouldContain "<a"
        }

        it("바로 앞에 단어문자가 붙어있으면(예: x@gildong) 변환되지 않는다") {
            // "abc#1"처럼 이슈 패턴 바로 앞에 문자가 오면 PATH_WITH_ISSUE_PATTERN(path#issue)이
            // 하나의 유효한 매치로 먼저 흡수해버려 단어 경계 억제 로직 자체가 발동하지 않는다 —
            // "@" 멘션 패턴(LOGIN_ID_PATTERN)으로 좌측 단어 경계만 순수하게 검증한다.
            val result = autoLinkRenderer.render("x@gildong", null)

            result shouldNotContain "<a"
            verify(exactly = 0) { userRepository.findByLoginId(any()) }
        }

        it("바로 뒤에 단어문자가 붙어있으면(예: #123abc) 변환되지 않는다") {
            val project = Project(id = 52L, owner = "owner", name = "proj52")

            val result = autoLinkRenderer.render("#1abc", project)

            result shouldNotContain "<a"
        }

        it("문자열 맨 앞에서 시작하는 매치는(직전 문자가 없어도) 정상적으로 변환된다") {
            val project = Project(id = 53L, owner = "owner", name = "proj53")
            val issue = Issue(title = "제목", project = project, number = 2L, state = State.OPEN)
            every { issueRepository.findByProjectAndNumber(project, 2L) } returns issue
            every { messageSource.getMessage(any(), null, any(), any()) } returns "열림"

            val result = autoLinkRenderer.render("#2 시작", project)

            result shouldContain "<a"
        }
    }

    describe("render() - <code>/<a> 태그 내부는 무시") {
        it("<code> 태그로 감싸진 텍스트 안의 #123은 변환되지 않는다") {
            val project = Project(id = 60L, owner = "owner", name = "proj60")

            val result = autoLinkRenderer.render("<code>#1</code>", project)

            result shouldNotContain "issueLink"
        }
    }
})
