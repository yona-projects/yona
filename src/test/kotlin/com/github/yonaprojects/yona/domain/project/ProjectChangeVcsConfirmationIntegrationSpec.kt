package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.orm.jpa.EntityManagerHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.WebApplicationContext
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ProjectChangeVcsConfirmationIntegrationSpec @Autowired constructor(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectService: ProjectService,
    private val repositoryService: RepositoryService,
    private val entityManagerFactory: EntityManagerFactory,
    private val transactionManager: PlatformTransactionManager,
    private val context: WebApplicationContext
) : AbstractIntegrationTest() {
    override fun extensions() = listOf(SpringExtension)

    companion object {
        private val root = Files.createTempDirectory("yona-reset-confirmation-")

        @JvmStatic
        @DynamicPropertySource
        fun storage(registry: DynamicPropertyRegistry) {
            listOf("git", "svn", "hg", "lfs", "upload").forEach { vcs ->
                registry.add("yona.$vcs.base-dir") { root.resolve(vcs).toString() }
            }
        }
    }

    private fun fixture(): Project {
        val owner = userRepository.save(User(loginId = "reset-${UUID.randomUUID().toString().take(8)}", name = "Reset test"))
        return projectService.createProject(Project(owner = owner.loginId, name = "reset", vcs = "GIT"), owner)
    }

    // Reproduce the request-scoped persistence context used by OSIV without an outer DB transaction.
    private fun withCachedProject(project: Project, action: () -> Unit) {
        val manager = entityManagerFactory.createEntityManager()
        TransactionSynchronizationManager.bindResource(entityManagerFactory, EntityManagerHolder(manager))
        try {
            val cached = manager.find(Project::class.java, project.id)
            cached.projectUsers.size shouldBe 1
            cached.forkingProjects.size shouldBe 0
            action()
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory)
            manager.close()
        }
    }

    private fun concurrently(action: () -> Unit) {
        CompletableFuture.runAsync {
            TransactionTemplate(transactionManager).executeWithoutResult { action() }
        }.get(60, TimeUnit.SECONDS)
    }

    init {
        afterSpec { root.toFile().deleteRecursively() }

        describe("repository reset confirmation with real Spring, JPA and filesystem") {
            it("resets through the real manager controller after the OSIV permission lookup") {
                val project = fixture()
                val oldDirectory = repositoryService.getRepository(project).getDirectory()
                val mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply<DefaultMockMvcBuilder>(springSecurity()).build()

                mockMvc.perform(post("/${project.owner}/${project.name}/changeVCS")
                    .with(user(project.owner!!)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectId":${project.id},"projectName":"reset","expectedVcs":"GIT","accepted":true}"""))
                    .andExpect(status().isNoContent)

                val saved = projectRepository.findById(project.id!!).orElseThrow()
                saved.id shouldBe project.id
                saved.vcs shouldBe "SUBVERSION"
                oldDirectory.exists() shouldBe false
                repositoryService.getRepository(saved).getDirectory().exists() shouldBe true
            }

            it("rejects a changed VCS even when the request persistence context cached the old value") {
                val project = fixture()
                val marker = repositoryService.getRepository(project).getDirectory().resolve("confirmation-marker")
                marker.writeText("must remain")
                withCachedProject(project) {
                    concurrently {
                        val changed = projectRepository.findById(project.id!!).orElseThrow()
                        changed.vcs = "SUBVERSION"
                        projectRepository.save(changed)
                    }
                    shouldThrow<VcsResetConflict> {
                        projectService.changeVCS(project.id!!, VcsResetConfirmation(project.id, project.name, "GIT", true))
                    }
                }

                projectRepository.findById(project.id!!).orElseThrow().vcs shouldBe "SUBVERSION"
                marker.readText() shouldBe "must remain"
                Files.exists(root.resolve("svn/${project.owner}/${project.name}")) shouldBe false
            }

            it("reloads child forks added after the request cached the project association") {
                val project = fixture()
                var forkId = 0L
                withCachedProject(project) {
                    concurrently {
                        val current = projectRepository.findById(project.id!!).orElseThrow()
                        forkId = projectRepository.save(Project(owner = project.owner, name = "new-fork", vcs = "GIT", originalProject = current)).id!!
                    }
                    projectService.changeVCS(project.id!!, VcsResetConfirmation(project.id, project.name, "GIT", true))
                }

                TransactionTemplate(transactionManager).executeWithoutResult {
                    projectRepository.findById(forkId).orElseThrow().originalProject shouldBe null
                    projectRepository.findById(project.id!!).orElseThrow().forkingProjects.size shouldBe 0
                }
            }
        }
    }
}
