package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@Transactional
class VcsServiceSpec @Autowired constructor(
    private val repositoryService: RepositoryService,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("VCS RepositoryService 저장소 생성 및 연동 테스트") {
            lateinit var owner: User
            lateinit var gitProject: Project
            lateinit var svnProject: Project

            beforeEach {
                projectRepository.deleteAll()
                userRepository.deleteAll()

                owner = User(
                    name = "김철수",
                    loginId = "chulsoo",
                    email = "chulsoo@example.com"
                )
                userRepository.save(owner)

                gitProject = Project(
                    name = "git-test-repo",
                    owner = "chulsoo",
                    vcs = "GIT"
                )
                projectRepository.save(gitProject)

                svnProject = Project(
                    name = "svn-test-repo",
                    owner = "chulsoo",
                    vcs = "SUBVERSION"
                )
                projectRepository.save(svnProject)
            }

            afterEach {
                try {
                    val gitRepo = repositoryService.getRepository(gitProject)
                    gitRepo.delete()
                } catch (e: Exception) {}

                try {
                    val svnRepo = repositoryService.getRepository(svnProject)
                    svnRepo.delete()
                } catch (e: Exception) {}
            }

            it("1. GIT 프로젝트에 대한 GitRepository 생성 및 초기화(create) 검증") {
                val repository = repositoryService.getRepository(gitProject)
                repository.shouldBeInstanceOf<GitRepository>()

                repository.isEmpty() shouldBe true
                repository.create()

                val gitDir = repository.getDirectory()
                gitDir.exists() shouldBe true
                repository.isEmpty() shouldBe true
            }

            it("2. SUBVERSION 프로젝트에 대한 SvnRepository 생성 및 초기화(create) 검증") {
                val repository = repositoryService.getRepository(svnProject)
                repository.shouldBeInstanceOf<SvnRepository>()

                repository.isEmpty() shouldBe true
                repository.create()

                val svnDir = repository.getDirectory()
                svnDir.exists() shouldBe true
                repository.isEmpty() shouldBe true
            }
        }
    }
}
