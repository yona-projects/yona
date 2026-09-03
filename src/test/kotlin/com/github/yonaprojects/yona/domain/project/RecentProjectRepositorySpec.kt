package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

// yona User.visits(Project)/RecentProject.addNew() 대응 (P2-09). ProjectViewController와
// git 프로토콜 진입점(GitServletConfig) 양쪽에서 공용으로 쓸 저장소 계층 방문 기록 메서드.
@Transactional
class RecentProjectRepositorySpec @Autowired constructor(
    private val recentProjectRepository: RecentProjectRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("RecentProjectRepository.recordVisit") {
            beforeEach {
                recentProjectRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("처음 방문하는 프로젝트면 최근 방문 목록에 새로 기록해야 한다") {
                val user = userRepository.save(User(loginId = "visitor1", name = "방문자1", email = "visitor1@yona.io"))
                val project = projectRepository.save(Project(name = "visited-project", owner = "someone"))

                recentProjectRepository.recordVisit(user, project)

                val recent = recentProjectRepository.findByUserId(user.id!!)
                recent.size shouldBe 1
                recent.first().projectId shouldBe project.id
                recent.first().owner shouldBe "someone"
            }

            it("같은 프로젝트를 다시 방문하면 중복 없이 최신 방문으로 갱신해야 한다") {
                val user = userRepository.save(User(loginId = "visitor2", name = "방문자2", email = "visitor2@yona.io"))
                val project = projectRepository.save(Project(name = "revisit-project", owner = "someone2"))

                recentProjectRepository.recordVisit(user, project)
                recentProjectRepository.recordVisit(user, project)

                val recent = recentProjectRepository.findByUserId(user.id!!)
                recent.size shouldBe 1
            }

            it("최근 방문 30건을 넘으면 가장 오래된 기록을 지워야 한다") {
                val user = userRepository.save(User(loginId = "visitor3", name = "방문자3", email = "visitor3@yona.io"))

                repeat(31) { i ->
                    val project = projectRepository.save(Project(name = "project-$i", owner = "someone3"))
                    recentProjectRepository.recordVisit(user, project)
                }

                val recent = recentProjectRepository.findByUserId(user.id!!)
                recent.size shouldBe 30
                recent.none { it.projectName == "project-0" } shouldBe true
            }

            // user.id ?: return — 아직 영속화되지 않아 id가 없는 User는 조용히 무시해야 한다
            // (예외를 던지지 않고, 아무 기록도 남기지 않아야 한다).
            it("user.id가 없으면(미영속) 아무 기록도 남기지 않고 조용히 무시해야 한다") {
                val unsavedUser = User(loginId = "novisitor", name = "미영속유저", email = "novisitor@yona.io")
                val project = projectRepository.save(Project(name = "project-noiduser", owner = "someone4"))

                recentProjectRepository.recordVisit(unsavedUser, project)

                recentProjectRepository.findAll().size shouldBe 0
            }

            // project.id ?: return — 아직 영속화되지 않아 id가 없는 Project는 조용히 무시해야 한다.
            it("project.id가 없으면(미영속) 아무 기록도 남기지 않고 조용히 무시해야 한다") {
                val user = userRepository.save(User(loginId = "visitor5", name = "방문자5", email = "visitor5@yona.io"))
                val unsavedProject = Project(name = "unsaved-project", owner = "someone5")

                recentProjectRepository.recordVisit(user, unsavedProject)

                recentProjectRepository.findByUserId(user.id!!).size shouldBe 0
            }
        }
    }
}
