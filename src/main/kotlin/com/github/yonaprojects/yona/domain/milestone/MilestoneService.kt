package com.github.yonaprojects.yona.domain.milestone

import com.github.yonaprojects.yona.domain.enumeration.State
import java.time.Instant

interface MilestoneService {
    // yona MilestoneApp.java의 MilestoneCondition(orderBy 기본값 "dueDate", orderDir 기본값 "asc")
    // + Milestone.java의 findMilestones(projectId, state) 2-인자 오버로드 대응. orderBy/orderDir을
    // 생략하면 기존 호출부(이슈 폼의 마일스톤 드롭다운 등)와 동일하게 동작한다.
    fun getMilestones(projectId: Long, state: State, orderBy: String = "dueDate", orderDir: String = "asc"): List<Milestone>
    fun getMilestone(milestoneId: Long): Milestone?
    fun createMilestone(projectId: Long, milestone: Milestone): Milestone
    fun updateMilestone(
        milestoneId: Long,
        title: String,
        contents: String?,
        dueDate: Instant?,
        state: State
    ): Milestone
    fun deleteMilestone(milestoneId: Long)
}
