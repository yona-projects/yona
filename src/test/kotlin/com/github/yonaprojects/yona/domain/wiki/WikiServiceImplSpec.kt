package com.github.yonaprojects.yona.domain.wiki

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.util.Optional

// WikiServiceImpl을 실제 파일시스템 bare git 저장소로 end-to-end 검증한다
// (GitRepositorySpec/BareCommitSpec과 동일한 패턴 — DB/Spring 컨텍스트 없이도 위키는 완전히
// git 파일 기반이라 순수 JVM 테스트로 충분하다).
class WikiServiceImplSpec : DescribeSpec({

    fun newService(gitBaseDir: String): WikiServiceImpl {
        val userRepository = mockk<UserRepository>()
        every { userRepository.findByEmail(any()) } returns Optional.empty()
        return WikiServiceImpl(userRepository, gitBaseDir, "main")
    }

    fun newProject(owner: String = "tester", name: String = "myproj") = Project(id = 1L, owner = owner, name = name)
    val user = User(id = 1L, loginId = "tester", name = "테스터", email = "tester@yona.io")

    describe("isInitialized / ensureRepository") {
        it("아직 저장소가 없으면 초기화되지 않은 상태다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()

            service.isInitialized(project) shouldBe false
        }

        it("ensureRepository() 호출 후에는 빈 bare 저장소가 만들어지지만, 커밋이 없으니 여전히 미초기화 취급한다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()

            service.ensureRepository(project)

            File(gitBaseDir, "tester/myproj.wiki.git").exists() shouldBe true
            service.isInitialized(project) shouldBe false
        }

        it("페이지를 하나 저장하면 초기화된 것으로 간주한다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()

            service.savePage(project, user, null, "Home", "# Home", null)

            service.isInitialized(project) shouldBe true
        }
    }

    describe("페이지 CRUD") {
        it("페이지를 생성하고 다시 읽으면 같은 내용을 반환한다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()

            service.savePage(project, user, null, "Home", "# Hello Wiki", null)

            val page = service.getPage(project, "Home")
            page shouldNotBe null
            page!!.content shouldBe "# Hello Wiki"
            page.path shouldBe "Home.md"
        }

        it("존재하지 않는 페이지는 null을 반환한다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()
            service.savePage(project, user, null, "Home", "# Hello", null)

            service.getPage(project, "NoSuchPage") shouldBe null
        }

        it("페이지를 수정하면 내용이 갱신된다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()
            service.savePage(project, user, null, "Home", "v1", null)

            service.savePage(project, user, "Home", "Home", "v2", null)

            service.getPage(project, "Home")!!.content shouldBe "v2"
        }

        it("페이지 이름을 바꾸면(rename) 옛 제목으로는 찾을 수 없고 새 제목으로 찾을 수 있다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()
            service.savePage(project, user, null, "OldTitle", "본문", null)

            service.savePage(project, user, "OldTitle", "NewTitle", "본문", null)

            service.getPage(project, "OldTitle") shouldBe null
            service.getPage(project, "NewTitle")!!.content shouldBe "본문"
        }

        it("페이지를 삭제하면 더 이상 조회되지 않는다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()
            service.savePage(project, user, null, "Home", "내용", null)

            service.deletePage(project, user, "Home", null)

            service.getPage(project, "Home") shouldBe null
        }

        it("listPages()는 저장된 모든 페이지를 제목순으로 반환한다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()
            service.savePage(project, user, null, "Zebra", "z", null)
            service.savePage(project, user, null, "Apple", "a", null)

            val titles = service.listPages(project).map { it.title }
            titles shouldBe listOf("Apple", "Zebra")
        }
    }

    describe("중첩 페이지(슬래시로 하위 경로 표현)") {
        it("슬래시가 포함된 제목은 하위 디렉터리 파일로 저장되고 그대로 읽힌다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()

            service.savePage(project, user, null, "Guides/Setup", "설치 안내", null)

            val page = service.getPage(project, "Guides/Setup")
            page shouldNotBe null
            page!!.path shouldBe "Guides/Setup.md"
        }

        it("listPages()가 중첩된 페이지도 재귀적으로 모두 나열한다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()
            service.savePage(project, user, null, "Home", "home", null)
            service.savePage(project, user, null, "Guides/Setup", "설치", null)
            service.savePage(project, user, null, "Guides/Advanced/Tuning", "튜닝", null)

            val titles = service.listPages(project).map { it.title }.toSet()
            titles shouldBe setOf("Home", "Guides/Setup", "Guides/Advanced/Tuning")
        }
    }

    describe("특수 페이지(Home/_Sidebar/_Footer)") {
        it("_Sidebar, _Footer, Home도 일반 페이지처럼 저장/조회된다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()

            service.savePage(project, user, null, "Home", "홈", null)
            service.savePage(project, user, null, "_Sidebar", "커스텀 사이드바", null)
            service.savePage(project, user, null, "_Footer", "커스텀 푸터", null)

            service.getPage(project, "Home")!!.content shouldBe "홈"
            service.getPage(project, "_Sidebar")!!.content shouldBe "커스텀 사이드바"
            service.getPage(project, "_Footer")!!.content shouldBe "커스텀 푸터"
        }
    }

    describe("검색") {
        it("제목 부분일치(대소문자 무시)로 검색한다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()
            service.savePage(project, user, null, "Getting Started", "1", null)
            service.savePage(project, user, null, "Guides/Advanced", "2", null)
            service.savePage(project, user, null, "FAQ", "3", null)

            val results = service.search(project, "guid").map { it.title }.toSet()
            results shouldBe setOf("Guides/Advanced")
        }
    }

    describe("히스토리 + diff") {
        it("history()는 그 페이지 파일의 커밋만 최신순으로 반환한다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()
            service.savePage(project, user, null, "Home", "v1", "Create Home")
            service.savePage(project, user, null, "Other", "x", "Create Other")
            service.savePage(project, user, "Home", "Home", "v2", "Update Home")

            val history = service.history(project, "Home")
            history.map { it.getShortMessage() } shouldBe listOf("Update Home", "Create Home")
        }

        it("diff()는 그 리비전이 그 페이지에 반영한 unified diff만 돌려준다(다른 페이지 변경은 섞이지 않는다)") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()
            service.savePage(project, user, null, "Home", "v1\n", "Create Home")
            val secondCommit = service.savePage(project, user, "Home", "Home", "v2\n", "Update Home")

            val patch = service.diff(project, "Home", secondCommit)
            patch shouldContain "Home.md"
            patch shouldContain "-v1"
            patch shouldContain "+v2"
        }

        it("compare()는 임의의 두 리비전 사이의 그 페이지 diff를 돌려준다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()
            val firstCommit = service.savePage(project, user, null, "Home", "v1\n", "Create Home")
            service.savePage(project, user, "Home", "Home", "v2\n", "Update Home")
            val thirdCommit = service.savePage(project, user, "Home", "Home", "v3\n", "Update Home again")

            val patch = service.compare(project, "Home", firstCommit, thirdCommit)
            patch shouldContain "-v1"
            patch shouldContain "+v3"
        }
    }

    describe("커밋 메시지 커스터마이징") {
        it("메시지를 지정하지 않으면 Create/Update/Rename 기본 메시지를 만든다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()

            service.savePage(project, user, null, "Home", "v1", null)
            service.savePage(project, user, "Home", "Home", "v2", null)
            service.savePage(project, user, "Home", "Renamed", "v2", null)

            // git log는(Forgejo와 마찬가지로 --follow 없이) 그 정확한 경로("Renamed.md")를 건드린
            // 커밋만 찾는다 — 이름변경 이전 경로("Home.md")였을 때의 커밋은 다른 파일 경로라
            // 별도 히스토리로 남는다(이름변경 커밋 자체만 "Renamed.md"를 건드림). 이름변경 전
            // 이력까지 이어 보려면 "Home"으로 history()를 호출해야 한다(아래 검증).
            val history = service.history(project, "Renamed")
            history.map { it.getShortMessage() } shouldBe listOf("Rename Home to Renamed")

            // "Home.md"의 히스토리에는 그 경로를 지운 이름변경 커밋도 포함된다(삭제 역시 그
            // 경로에 대한 변경이므로 git log의 경로 필터에 걸린다).
            val oldHistory = service.history(project, "Home")
            oldHistory.map { it.getShortMessage() } shouldBe listOf("Rename Home to Renamed", "Update Home", "Create Home")
        }

        it("메시지를 지정하면 그대로 커밋 메시지가 된다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()

            service.savePage(project, user, null, "Home", "v1", "이건 내가 쓴 메시지")

            service.history(project, "Home").first().getShortMessage() shouldBe "이건 내가 쓴 메시지"
        }
    }

    describe("제목 검증") {
        it("예약된 라우팅 세그먼트로 시작하는 제목은 거부한다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()

            shouldThrow<IllegalArgumentException> {
                service.savePage(project, user, null, "_new", "내용", null)
            }
        }

        it("경로 탈출(..)이 포함된 제목은 거부한다") {
            val gitBaseDir = Files.createTempDirectory("yona-wiki-test").toFile().absolutePath
            val service = newService(gitBaseDir)
            val project = newProject()

            shouldThrow<IllegalArgumentException> {
                service.savePage(project, user, null, "../etc/passwd", "내용", null)
            }
        }
    }
})
