package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
class TemplateHelperSpec @Autowired constructor(
    private val templateHelper: TemplateHelper,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository
) : AbstractIntegrationTest() {

    init {
        describe("TemplateHelper 멤버십 및 권한 검증 테스트") {
            lateinit var project: Project
            lateinit var memberUser: User
            lateinit var nonMemberUser: User
            lateinit var managerUser: User
            lateinit var managerRole: Role
            lateinit var memberRole: Role

            beforeEach {
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()

                memberUser = userRepository.save(User(loginId = "member1", name = "멤버1", email = "m1@yona.io"))
                nonMemberUser = userRepository.save(User(loginId = "nonmember", name = "외부인", email = "nm@yona.io"))
                managerUser = userRepository.save(User(loginId = "manager1", name = "관리자1", email = "mgr@yona.io"))
                
                project = projectRepository.save(Project(name = "test-menu-project", owner = "manager1", vcs = "GIT"))

                managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "manager"))
                }
                memberRole = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "member"))
                }

                // 권한 매핑
                projectUserRepository.save(ProjectUser(project = project, user = memberUser, role = memberRole))
                projectUserRepository.save(ProjectUser(project = project, user = managerUser, role = managerRole))
            }

            it("[Test-16-1-1] isMember 헬퍼는 가입 회원에 대해 true, 미가입 회원에 대해 false를 반환해야 한다") {
                templateHelper.isMember(project, memberUser) shouldBe true
                templateHelper.isMember(project, managerUser) shouldBe true
                templateHelper.isMember(project, nonMemberUser) shouldBe false
                templateHelper.isMember(project, null) shouldBe false
            }

            it("[Test-16-1-2] isManager 헬퍼는 매니저 회원에 대해 true, 일반 회원 및 외부인에 대해 false를 반환해야 한다") {
                templateHelper.isManager(project, managerUser) shouldBe true
                templateHelper.isManager(project, memberUser) shouldBe false
                templateHelper.isManager(project, nonMemberUser) shouldBe false
                templateHelper.isManager(project, null) shouldBe false
            }
        }
    }
}
