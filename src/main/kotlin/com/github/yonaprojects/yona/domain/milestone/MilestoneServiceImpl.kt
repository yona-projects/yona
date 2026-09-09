package com.github.yonaprojects.yona.domain.milestone

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.watch.WatchService

@Service
@Transactional(readOnly = true)
class MilestoneServiceImpl(
    private val milestoneRepository: MilestoneRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val attachmentService: AttachmentService,
    private val watchService: WatchService
) : MilestoneService {

    override fun getMilestones(projectId: Long, state: State, orderBy: String, orderDir: String): List<Milestone> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }

        // yona Milestone.findMilestones(projectId, state, sort, direction) 대응.
        // completionRate는 계산 필드(DB 컬럼 아님)라 DB 정렬 대상에서 제외하고
        // 정렬 없이 조회한 뒤, 컨트롤러가 DTO 변환 후 completionRate 기준으로 별도 정렬한다
        // (legacy도 동일하게 findMilestones() 안에서 조회 후 Collections.sort()로 재정렬).
        val direction = if (orderDir.equals("desc", ignoreCase = true)) Sort.Direction.DESC else Sort.Direction.ASC
        val sort = if (orderBy == "completionRate") Sort.unsorted() else Sort.by(direction, orderBy)

        return if (state == State.ALL) {
            milestoneRepository.findByProject(project, sort)
        } else {
            milestoneRepository.findByProjectAndState(project, state, sort)
        }
    }

    override fun getMilestone(milestoneId: Long): Milestone? {
        return milestoneRepository.findById(milestoneId).orElse(null)
    }

    @Transactional
    override fun createMilestone(projectId: Long, milestone: Milestone): Milestone {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }

        val existing = milestoneRepository.findByProjectAndTitle(project, milestone.title)
        if (existing != null) {
            throw IllegalArgumentException("이미 동일한 제목의 마일스톤이 존재합니다.")
        }

        milestone.project = project
        milestone.state = State.OPEN
        return milestoneRepository.save(milestone)
    }

    @Transactional
    override fun updateMilestone(
        milestoneId: Long,
        title: String,
        contents: String?,
        dueDate: Instant?,
        state: State
    ): Milestone {
        val milestone = milestoneRepository.findById(milestoneId)
            .orElseThrow { IllegalArgumentException("마일스톤을 찾을 수 없습니다.") }

        val existing = milestoneRepository.findByProjectAndTitle(milestone.project, title.trim())
        if (existing != null && existing.id != milestoneId) {
            throw IllegalArgumentException("이미 동일한 제목의 마일스톤이 존재합니다.")
        }

        milestone.title = title.trim()
        milestone.contents = contents
        milestone.dueDate = dueDate
        milestone.state = state

        return milestoneRepository.save(milestone)
    }

    @Transactional
    override fun deleteMilestone(milestoneId: Long) {
        val milestone = milestoneRepository.findById(milestoneId)
            .orElseThrow { IllegalArgumentException("마일스톤을 찾을 수 없습니다.") }
        
        // 연관 관계 일괄 끊기
        issueRepository.removeMilestoneFromIssues(milestone)
        
        attachmentService.deleteAll(ResourceType.MILESTONE, milestone.id.toString())
        // yona ResourcePersistAdapter.postDelete() 대응.
        watchService.deleteAll(ResourceType.MILESTONE, milestone.id.toString())
        milestoneRepository.delete(milestone)
    }
}
