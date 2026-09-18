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
    // name과 동일한 "null이면 현재 값 유지" 규칙 — /api/projects/{id} PUT을 부분 업데이트로도
    // 호출하는 곳(project/home.html 설명 인라인 위젯)이 있어, 여기 없는 필드를 고정 기본값으로
    // 채우면 그 호출 하나가 나머지 프로젝트 설정을 전부 조용히 리셋해버린다.
    val projectScope: ProjectScope? = null,
    val isCodeAccessibleMemberOnly: Boolean? = null,
    val isUsingReviewerCount: Boolean? = null,
    val defaultReviewerCount: Int? = null,
    val defaultBranch: String? = null,
    val isCodeEnabled: Boolean? = null,
    val isIssueEnabled: Boolean? = null,
    val isPullRequestEnabled: Boolean? = null,
    val isReviewEnabled: Boolean? = null,
    val isMilestoneEnabled: Boolean? = null,
    val isBoardEnabled: Boolean? = null,
    val isWikiEnabled: Boolean? = null
)

