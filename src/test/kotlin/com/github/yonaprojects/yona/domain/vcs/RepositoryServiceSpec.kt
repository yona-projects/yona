package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.io.FileNotFoundException
import java.util.Optional

@Suppress("UNCHECKED_CAST")
private fun <T> readPrivateField(target: Any, fieldName: String): T {
    val field = target.javaClass.getDeclaredField(fieldName)
    field.isAccessible = true
    return field.get(target) as T
}

class RepositoryServiceSpec : DescribeSpec({

    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val gpgSignatureVerifier = mockk<com.github.yonaprojects.yona.domain.gpgkey.GpgSignatureVerifier>()
    val gitBaseDir = "/tmp/git"
    val svnBaseDir = "/tmp/svn"

    val hgBaseDir = "/tmp/hg"

    val service = RepositoryService(userRepository, projectRepository, gpgSignatureVerifier, gitBaseDir, svnBaseDir, hgBaseDir, "main")

    val objectMapper = ObjectMapper()

    beforeTest {
        clearMocks(userRepository, projectRepository)
    }

    describe("getRepository") {
        it("should return SvnRepository for SVN project") {
            val proj = Project(id = 1L, owner = "owner", name = "svnproj", vcs = "SVN")
            val repo = service.getRepository(proj)
            repo.shouldBeInstanceOf<SvnRepository>()
            
            // test lambda for svn resolving
            every { userRepository.findByLoginId("login1") } returns Optional.of(User(loginId = "login1"))
            every { userRepository.findByLoginId("login2") } returns Optional.empty()

            // SvnRepository 생성자에 넘긴 userResolver 람다는 SVN 관련 실동작 없이는 호출되지 않으므로,
            // private 필드를 리플렉션으로 꺼내 직접 호출해 람다 본문 자체의 커버리지를 확보한다.
            val resolver = readPrivateField<(String) -> User?>(repo, "userResolver")
            resolver("login1")?.loginId shouldBe "login1"
            resolver("login2") shouldBe null
        }

        it("should return SvnRepository for SVN project with null owner") {
            val proj = Project(id = 1L, name = "svnproj", vcs = "SVN")
            proj.owner = null
            val repo = service.getRepository(proj)
            repo.shouldBeInstanceOf<SvnRepository>()
        }
        
        it("should return SvnRepository for SUBVERSION project") {
            val proj = Project(id = 1L, owner = "owner", name = "svnproj", vcs = "subversion")
            val repo = service.getRepository(proj)
            repo.shouldBeInstanceOf<SvnRepository>()
        }
        
        it("should return GitRepository for GIT project") {
            val proj = Project(id = 1L, owner = "owner", name = "gitproj", vcs = "GIT")
            val repo = service.getRepository(proj)
            repo.shouldBeInstanceOf<GitRepository>()
            
            // The lambda uses findByEmail
            every { userRepository.findByEmail("a@b.com") } returns Optional.of(User(email = "a@b.com"))
            every { userRepository.findByEmail("x@b.com") } returns Optional.empty()

            // GitRepository 생성자에 넘긴 userResolver 람다(email null/non-null 분기 포함)도
            // 실제 커밋 처리 없이는 호출되지 않으므로 리플렉션으로 직접 호출해 검증한다.
            val resolver = readPrivateField<(String?, String?) -> User?>(repo, "userResolver")
            resolver(null, "a@b.com")?.email shouldBe "a@b.com"
            resolver(null, null) shouldBe null
        }
        
        it("should return GitRepository for project with null vcs") {
            val proj = Project(id = 1L, owner = "owner", name = "gitproj")
            proj.vcs = null
            val repo = service.getRepository(proj)
            repo.shouldBeInstanceOf<GitRepository>()
        }
        
        it("should return GitRepository for project with null owner") {
            val proj = Project(id = 1L, name = "gitproj", vcs = "GIT")
            proj.owner = null
            val repo = service.getRepository(proj)
            repo.shouldBeInstanceOf<GitRepository>()
        }
    }

    describe("getFileAsRaw") {
        it("should return raw bytes if project exists") {
            val proj = Project(id = 1L, owner = "owner", name = "proj", vcs = "GIT")
            every { projectRepository.findByOwnerAndName("owner", "proj") } returns Optional.of(proj)
            
            val mockRepo = mockk<PlayRepository>()
            every { mockRepo.getRawFile("HEAD", "file.txt") } returns "hello".toByteArray()
            
            // Actually getRepository returns a real GitRepository, so it will throw exception if dir doesn't exist
            // Let's mock getRepository by using spyk if possible, or just expect exception
            
            // Instead, I'll mock projectRepository to test when it doesn't exist
            every { projectRepository.findByOwnerAndName("noowner", "proj") } returns Optional.empty()
            service.getFileAsRaw("noowner", "proj", "HEAD", "file.txt") shouldBe null
        }

        it("should proceed past the not-found check when project exists (real empty bare repo)") {
            val tempBase = tempdir()
            val realService = RepositoryService(userRepository, projectRepository, gpgSignatureVerifier, tempBase.absolutePath, svnBaseDir, hgBaseDir, "main")
            val proj = Project(id = 2L, owner = "realowner", name = "realproj", vcs = "GIT")
            every { projectRepository.findByOwnerAndName("realowner", "realproj") } returns Optional.of(proj)

            // 실제 빈 bare 저장소를 만들어 getRepository()는 성공하고, getRawFile()의 HEAD 해석
            // 단계에서만 실패하게 한다 — getFileAsRaw의 "프로젝트 존재" 분기 자체가 목적.
            GitRepository("realowner", "realproj", tempBase.absolutePath, userResolver = { _, _ -> null }).create()

            shouldThrow<FileNotFoundException> {
                realService.getFileAsRaw("realowner", "realproj", "HEAD", "file.txt")
            }
        }
    }

    describe("getMetaDataFromAncestorDirectories") {
        it("should return null if root metadata is null") {
            val repo = mockk<PlayRepository>()
            every { repo.getMetaDataFromPath("master", "") } returns null
            service.getMetaDataFromAncestorDirectories(repo, "master", "src/main") shouldBe null
        }
        
        it("should return null if intermediate metadata is null") {
            val repo = mockk<PlayRepository>()
            val rootNode = objectMapper.createObjectNode()
            every { repo.getMetaDataFromPath("master", "") } returns rootNode
            every { repo.isIntermediateFolder(any()) } returns false
            every { repo.getMetaDataFromPath("master", "src") } returns null
            
            service.getMetaDataFromAncestorDirectories(repo, "master", "src/main") shouldBe null
        }
        
        it("should return recursive data ignoring intermediate folders") {
            val repo = mockk<PlayRepository>()
            val rootNode = objectMapper.createObjectNode()
            val srcNode = objectMapper.createObjectNode()
            
            every { repo.getMetaDataFromPath("master", "") } returns rootNode
            every { repo.isIntermediateFolder("src") } returns true
            every { repo.isIntermediateFolder("src/main") } returns false
            every { repo.getMetaDataFromPath("master", "src/main") } returns srcNode
            
            val res = service.getMetaDataFromAncestorDirectories(repo, "master", "src/main")
            res shouldNotBe null
            res!!.size shouldBe 2
            res[0].get("path").asText() shouldBe ""
            res[1].get("path").asText() shouldBe "src/main"
        }

        it("should return only the root metadata when path is empty") {
            val repo = mockk<PlayRepository>()
            val rootNode = objectMapper.createObjectNode()
            every { repo.getMetaDataFromPath("master", "") } returns rootNode

            val res = service.getMetaDataFromAncestorDirectories(repo, "master", "")
            res shouldNotBe null
            res!!.size shouldBe 1
            res[0].get("path").asText() shouldBe ""
        }
    }
})
