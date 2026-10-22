package dev.yona.domain.project

import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.issue.MilestoneStatus
import dev.yona.domain.issue.MilestoneStatusService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.ProjectReadmeService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 프로젝트 홈이 보여 주는 정보를 모은다. v1.6 `home.scala.html`과 `partial_readme.scala.html`이 모델 메서드로
 * 구하던 값들이다.
 */
@Service
@Transactional(readOnly = true)
class ProjectHomeService(
    private val users: UserRepository,
    private val postings: PostingRepository,
    private val readme: ProjectReadmeService,
    private val milestones: MilestoneStatusService
) {

    /** 프로젝트 구성원이다. 사이트 관리자 역할은 빼고 이름 오름차순이다 (v1.6 `User.findUsersByProject`). */
    fun members(project: Project): List<User> = users.findMembersOfProject(requireNotNull(project.id), RoleType.SITEMANAGER.id)

    /** README 파일 이름이다. 없으면 `null`이다. */
    fun readmeFileName(project: Project): String? = readme.fileName(project)

    /** README 파일 내용이다. 없으면 `null`이다. */
    fun readmeText(project: Project): String? = readme.read(project)

    /** 코드 메뉴를 끈 프로젝트의 README 게시글 본문이다. 없으면 `null`이다 (v1.6 `Posting.findREADMEPosting`). */
    fun readmePostingBody(project: Project): String? = postings.findFirstByProjectIdAndReadmeTrue(requireNotNull(project.id))?.body

    /** 마감일이 가장 이른 열린 마일스톤의 현황이다. 열린 마일스톤이 없으면 `null`이다. */
    fun firstOpenMilestone(project: Project): MilestoneStatus? =
        milestones.findOpenMilestones(requireNotNull(project.id)).firstOrNull()?.let { milestones.statusOf(it) }
}
