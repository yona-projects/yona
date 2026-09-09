package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.domain.user.User

interface ProjectService {
    fun findByOwnerAndName(owner: String, name: String): Project?
    fun findProjectsByOwner(owner: String): List<Project>
    fun createProject(project: Project, creator: User): Project
    fun exists(owner: String, name: String): Boolean
    fun isMember(projectId: Long, loginId: String): Boolean
    fun updateProject(projectId: Long, param: UpdateProjectParam): Project
    fun deleteProject(projectId: Long)
    fun requestNewTransfer(projectId: Long, senderId: Long, destination: String): ProjectTransfer
    fun acceptTransfer(transferId: Long, confirmKey: String, acceptorId: Long)
    fun forkProject(projectId: Long, forkerId: Long, destinationOwner: String = "", destinationName: String = ""): Project
    fun changeVCS(projectId: Long): Project

    // yona ProjectApp.labels/attachLabel/detachLabel 대응
    fun getProjectLabels(projectId: Long): Set<Label>
    fun attachLabel(projectId: Long, category: String?, name: String): AttachLabelResult
    fun detachLabel(projectId: Long, labelId: Long): Boolean
}

data class AttachLabelResult(
    val label: Label,
    val isCreated: Boolean,
    val isAttached: Boolean
)

data class UpdateProjectParam(
    // yona ProjectApp.settingProject()의 이름 변경(개명) 분기 대응. null/현재 이름과 동일하면
    // 변경하지 않는다 — 지정되면 소유자는 그대로 두고 이름만 바꾼다(소유권 이전과는 다른 별개 경로).
    val name: String? = null,
    val overview: String,
    val projectScope: ProjectScope,
    val isCodeAccessibleMemberOnly: Boolean,
    val isUsingReviewerCount: Boolean,
    val defaultReviewerCount: Int,
    val defaultBranch: String?,
    val isCodeEnabled: Boolean,
    val isIssueEnabled: Boolean,
    val isPullRequestEnabled: Boolean,
    val isReviewEnabled: Boolean,
    val isMilestoneEnabled: Boolean,
    val isBoardEnabled: Boolean,
    val isWikiEnabled: Boolean
)

