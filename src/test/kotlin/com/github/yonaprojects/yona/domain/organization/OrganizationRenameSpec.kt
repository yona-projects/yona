package com.github.yonaprojects.yona.domain.organization

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.FavoriteOrganization
import com.github.yonaprojects.yona.domain.user.FavoriteOrganizationRepository
import com.github.yonaprojects.yona.domain.user.FavoriteProject
import com.github.yonaprojects.yona.domain.user.FavoriteProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.TreeFormatter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID

// No test transaction: exercise the service's commit/rollback and filesystem compensation.
class OrganizationRenameSpec @Autowired constructor(
    private val organizationService: OrganizationService,
    private val organizationRepository: OrganizationRepository,
    private val projectRepository: ProjectRepository,
    private val repositoryService: RepositoryService,
    private val userRepository: UserRepository,
    private val favoriteOrganizationRepository: FavoriteOrganizationRepository,
    private val favoriteProjectRepository: FavoriteProjectRepository
) : AbstractIntegrationTest() {
    companion object {
        private val repositoryRoot = Files.createTempDirectory("yona-org-rename").toFile()
        private val lfsRoot = Files.createTempDirectory("yona-org-rename-lfs").toFile()

        @JvmStatic
        @DynamicPropertySource
        fun repositoryProperties(registry: DynamicPropertyRegistry) {
            registry.add("yona.git.base-dir") { repositoryRoot.absolutePath }
            registry.add("yona.lfs.base-dir") { lfsRoot.absolutePath }
        }
    }

    init {
        describe("organization rename repository parity") {
            lateinit var organization: Organization
            lateinit var user: User
            lateinit var oldOwner: String
            lateinit var newOwner: String
            val projects = mutableListOf<Project>()
            val projectFavorites = mutableListOf<FavoriteProject>()
            lateinit var organizationFavorite: FavoriteOrganization

            fun createProject(name: String): Project {
                val project = projectRepository.save(Project(name = name, owner = oldOwner, vcs = "GIT", organization = organization))
                projects.add(project)
                return project
            }

            fun seedRepository(directory: File, content: String): String {
                Git.init().setDirectory(directory).setBare(true).call().use { git ->
                    val repo = git.repository
                    repo.newObjectInserter().use { inserter ->
                        val tree = TreeFormatter()
                        tree.append("README.md", FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, content.toByteArray()))
                        val commit = CommitBuilder()
                        commit.setTreeId(inserter.insert(tree))
                        commit.author = PersonIdent("Rename Test", "rename@yona.io")
                        commit.committer = commit.author
                        commit.message = "Repository content must survive organization rename"
                        val id = inserter.insert(commit)
                        inserter.flush()
                        repo.updateRef("refs/heads/main").apply { setNewObjectId(id) }.update()
                        repo.updateRef("HEAD").link("refs/heads/main")
                        return id.name
                    }
                }
            }

            fun assertOriginalState(project: Project, commit: String) {
                organizationRepository.findById(organization.id!!).orElseThrow().name shouldBe oldOwner
                val persisted = projectRepository.findById(project.id!!).orElseThrow()
                persisted.owner shouldBe oldOwner
                repositoryService.getRepository(persisted).getRawFile(commit, "README.md")?.decodeToString() shouldBe "original content"
                favoriteOrganizationRepository.findById(organizationFavorite.id!!).orElseThrow().organizationName shouldBe oldOwner
            }

            beforeEach {
                val suffix = UUID.randomUUID().toString().take(8)
                oldOwner = "before-$suffix"
                newOwner = "after-$suffix"
                user = userRepository.save(User(loginId = "user-$suffix", name = "Rename Test", email = "$suffix@yona.io"))
                organization = organizationService.createOrganization(Organization(name = oldOwner, descr = "original description"))
                organizationFavorite = favoriteOrganizationRepository.save(FavoriteOrganization(user = user, organization = organization))
            }

            afterEach {
                projectFavorites.forEach { favoriteProjectRepository.deleteById(it.id!!) }
                projectFavorites.clear()
                favoriteOrganizationRepository.deleteById(organizationFavorite.id!!)
                projects.forEach { projectRepository.deleteById(it.id!!) }
                projects.clear()
                organizationRepository.deleteById(organization.id!!)
                userRepository.deleteById(user.id!!)
                File(repositoryRoot, oldOwner).deleteRecursively()
                File(repositoryRoot, newOwner).deleteRecursively()
                File(lfsRoot, oldOwner).deleteRecursively()
                File(lfsRoot, newOwner).deleteRecursively()
            }

            it("moves every child repository preserving commits, ownership, favorites and old URL lookup") {
                val commits = listOf("first", "second").associate { name ->
                    val project = createProject(name)
                    projectFavorites.add(favoriteProjectRepository.save(FavoriteProject(user = user, project = project)))
                    project to seedRepository(repositoryService.getRepository(project).getDirectory(), "content for $name")
                }
                val wikiCommits = commits.keys.associateWith { project ->
                    File(lfsRoot, "$oldOwner/${project.name}/object").apply {
                        parentFile.mkdirs()
                        writeText("LFS payload for ${project.name}")
                    }
                    seedRepository(File(repositoryRoot, "$oldOwner/${project.name}.wiki.git"), "wiki content")
                }

                organizationService.updateOrganizationSettings(organization.id!!, newOwner, "renamed description", user.id!!)

                organizationRepository.findById(organization.id!!).orElseThrow().name shouldBe newOwner
                favoriteOrganizationRepository.findById(organizationFavorite.id!!).orElseThrow().organizationName shouldBe newOwner
                commits.forEach { (project, commit) ->
                    val persisted = projectRepository.findByOwnerAndName(newOwner, project.name).orElseThrow()
                    persisted.id shouldBe project.id
                    persisted.organization?.id shouldBe organization.id
                    projectRepository.findByOwnerAndNameOrPreviousPlace(oldOwner, project.name).orElseThrow().id shouldBe project.id
                    repositoryService.getRepository(project).getDirectory().exists() shouldBe false
                    val moved = repositoryService.getRepository(persisted)
                    moved.getDirectory() shouldBe File(repositoryRoot, "$newOwner/${project.name}.git")
                    moved.getRawFile("HEAD", "README.md")?.decodeToString() shouldBe "content for ${project.name}"
                    moved.getRawFile(commit, "README.md")?.decodeToString() shouldBe "content for ${project.name}"
                    favoriteProjectRepository.findByProjectId(project.id!!).single().owner shouldBe newOwner
                    val wiki = File(repositoryRoot, "$newOwner/${project.name}.wiki.git")
                    Git.open(wiki).use { it.repository.resolve("HEAD").name shouldBe wikiCommits[project] }
                    File(repositoryRoot, "$oldOwner/${project.name}.wiki.git").exists() shouldBe false
                    File(lfsRoot, "$newOwner/${project.name}/object").readText() shouldBe "LFS payload for ${project.name}"
                    File(lfsRoot, "$oldOwner/${project.name}").exists() shouldBe false
                }
            }

            it("description-only updates leave ownership, history and repository untouched") {
                val project = createProject("description-only")
                val commit = seedRepository(repositoryService.getRepository(project).getDirectory(), "original content")

                organizationService.updateOrganizationSettings(organization.id!!, oldOwner, "changed description", user.id!!)

                assertOriginalState(project, commit)
                organizationRepository.findById(organization.id!!).orElseThrow().descr shouldBe "changed description"
                projectRepository.findById(project.id!!).orElseThrow().previousOwnerLoginId shouldBe null
            }

            it("rejects invalid and reserved rename targets before touching repositories or metadata") {
                val project = createProject("validated")
                val commit = seedRepository(repositoryService.getRepository(project).getDirectory(), "original content")
                listOf("../escaped", "a/b", "", "my org", "projects").forEach { invalid ->
                    shouldThrow<IllegalArgumentException> {
                        organizationService.updateOrganizationSettings(organization.id!!, invalid, "changed", user.id!!)
                    }
                    assertOriginalState(project, commit)
                    organizationRepository.findById(organization.id!!).orElseThrow().descr shouldBe "original description"
                }
            }

            it("refuses an existing destination without losing either repository") {
                val project = createProject("collision")
                val commit = seedRepository(repositoryService.getRepository(project).getDirectory(), "original content")
                val destination = File(repositoryRoot, "$newOwner/${project.name}.git")
                val destinationCommit = seedRepository(destination, "unrelated repository")

                shouldThrow<IllegalStateException> {
                    organizationService.updateOrganizationSettings(organization.id!!, newOwner, "changed", user.id!!)
                }

                assertOriginalState(project, commit)
                val target = repositoryService.getRepository(Project(owner = newOwner, name = project.name, vcs = "GIT"))
                target.getRawFile(destinationCommit, "README.md")?.decodeToString() shouldBe "unrelated repository"
            }

            it("rolls back metadata when the repository destination cannot be created") {
                val project = createProject("blocked")
                val commit = seedRepository(repositoryService.getRepository(project).getDirectory(), "original content")
                File(repositoryRoot, newOwner).writeText("blocking file")

                shouldThrow<IOException> {
                    organizationService.updateOrganizationSettings(organization.id!!, newOwner, "changed", user.id!!)
                }

                assertOriginalState(project, commit)
                File(repositoryRoot, newOwner).readText() shouldBe "blocking file"
            }

            it("restores all moved repositories when the database commit fails") {
                val commits = listOf("rollback-first", "rollback-second").associate { name ->
                    val project = createProject(name)
                    project to seedRepository(repositoryService.getRepository(project).getDirectory(), "original content")
                }
                val wikiCommits = commits.keys.associateWith { project ->
                    File(lfsRoot, "$oldOwner/${project.name}/object").apply {
                        parentFile.mkdirs()
                        writeText("original LFS payload")
                    }
                    seedRepository(File(repositoryRoot, "$oldOwner/${project.name}.wiki.git"), "original wiki")
                }

                shouldThrow<org.springframework.dao.DataIntegrityViolationException> {
                    // Organization.descr is varchar(255); failure occurs after repository moves.
                    organizationService.updateOrganizationSettings(organization.id!!, newOwner, "x".repeat(256), user.id!!)
                }

                commits.forEach { (project, commit) ->
                    assertOriginalState(project, commit)
                    File(repositoryRoot, "$newOwner/${project.name}.git").exists() shouldBe false
                    Git.open(File(repositoryRoot, "$oldOwner/${project.name}.wiki.git")).use {
                        it.repository.resolve("HEAD").name shouldBe wikiCommits[project]
                    }
                    File(repositoryRoot, "$newOwner/${project.name}.wiki.git").exists() shouldBe false
                    File(lfsRoot, "$oldOwner/${project.name}/object").readText() shouldBe "original LFS payload"
                    File(lfsRoot, "$newOwner/${project.name}").exists() shouldBe false
                }
                organizationRepository.findById(organization.id!!).orElseThrow().descr shouldBe "original description"
            }

            it("renames projects without a physical repository using the existing move no-op contract") {
                val project = createProject("no-repository")
                organizationService.updateOrganizationSettings(organization.id!!, newOwner, null, user.id!!)
                projectRepository.findById(project.id!!).orElseThrow().owner shouldBe newOwner
                File(repositoryRoot, "$newOwner/${project.name}.git").exists() shouldBe false
            }
        }
        afterSpec {
            repositoryRoot.deleteRecursively()
            lfsRoot.deleteRecursively()
        }
    }
}
