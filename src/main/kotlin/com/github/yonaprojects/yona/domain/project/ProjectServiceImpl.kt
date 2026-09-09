package com.github.yonaprojects.yona.domain.project

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.vcs.nextVcsInCycle
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.FavoriteProjectRepository
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.issue.AssigneeRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategoryRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelService
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.board.PostingService
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestCommitRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEventRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.webhook.WebhookRepository
import com.github.yonaprojects.yona.domain.webhook.WebhookThreadRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.watch.WatchService
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Optional

@Service
@Transactional(readOnly = true)
class ProjectServiceImpl(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val repositoryService: RepositoryService,
    private val userRepository: UserRepository,
    private val projectTransferRepository: ProjectTransferRepository,
    private val roleRepository: RoleRepository,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val labelRepository: LabelRepository,
    // 프로젝트 삭제 계단식 정리에 필요한 의존성.
    private val issueRepository: IssueRepository,
    private val issueService: IssueService,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val issueLabelService: IssueLabelService,
    private val assigneeRepository: AssigneeRepository,
    private val webhookRepository: WebhookRepository,
    private val webhookThreadRepository: WebhookThreadRepository,
    private val postingRepository: PostingRepository,
    private val postingService: PostingService,
    private val commentThreadRepository: CommentThreadRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val pullRequestCommitRepository: PullRequestCommitRepository,
    private val favoriteProjectRepository: FavoriteProjectRepository,
    private val watchService: WatchService,
    // 크로스플랫폼/운영 경로 설정 버그 수정 — 다른 서비스(RepositoryService 등)와 동일하게
    // 물리 저장소 base-dir을 설정으로 주입받는다. 이전에는 acceptTransfer/forkProject 두 곳에
    // "/tmp/yona/git", "/tmp/yona/svn"이 리터럴로 하드코딩돼 있어, yona.git.base-dir/
    // yona.svn.base-dir을 다른 경로로 바꿔도 이 두 기능만 그 설정을 무시했다.
    @Value("\${yona.git.base-dir:/tmp/yona/git}")
    private val gitBaseDir: String,
    @Value("\${yona.svn.base-dir:/tmp/yona/svn}")
    private val svnBaseDir: String,
    @Value("\${yona.hg.base-dir:/tmp/yona/hg}")
    private val hgBaseDir: String
) : ProjectService {

    // 프로젝트가 이전/개명된 뒤에도 이 서비스 메서드를 쓰는 모든 호출부(SVN/Git 인가 필터 등)가
    // 자동으로 예전 owner/name도 계속 찾을 수 있다.
    override fun findByOwnerAndName(owner: String, name: String): Project? {
        return projectRepository.findByOwnerAndNameOrPreviousPlace(owner, name).orElse(null)
    }

    override fun findProjectsByOwner(owner: String): List<Project> {
        return projectRepository.findByOwner(owner)
    }

    @Transactional
    override fun createProject(project: Project, creator: User): Project {
        if (ProjectNameValidator.isRestricted(project.name)) {
            throw IllegalArgumentException("Project name is restricted: ${project.name}")
        }
        if (exists(project.owner ?: "", project.name)) {
            throw IllegalArgumentException("Already exists project name: ${project.owner}/${project.name}")
        }
        project.createdDate = Instant.now()
        project.siteurl = "http://localhost:9000/${project.name}"
        val savedProject = projectRepository.save(project)
        roleRepository.findById(RoleType.MANAGER.roleType).ifPresent { managerRole ->
            val projectUser = ProjectUser(
                project = savedProject,
                user = creator,
                role = managerRole
            )
            projectUserRepository.save(projectUser)
            savedProject.projectUsers.add(projectUser)
        }
        // 이 호출이 없으면 DB 행만 생기고 물리 bare 저장소가 안 만들어져, 이후 README 커밋 등
        // 저장소 쓰기 작업이 BareCommit의 조용한 catch(Exception)에 가려진 채 전부 실패한다.
        repositoryService.getRepository(savedProject).create()
        return savedProject
    }

    override fun exists(owner: String, name: String): Boolean {
        return projectRepository.findByOwnerAndName(owner, name).isPresent
    }

    override fun isMember(projectId: Long, loginId: String): Boolean {
        val project = projectRepository.findById(projectId).orElse(null) ?: return false
        val user = userRepository.findByLoginId(loginId).orElse(null)
        if (user != null && user.isSiteManager) return true
        if (project.owner == loginId) return true
        return projectUserRepository.existsByProjectIdAndUserLoginId(projectId, loginId)
    }

    @Transactional
    override fun updateProject(projectId: Long, param: UpdateProjectParam): Project {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }

        // 개명 검사를 가장 먼저 수행해, 다른 필드가 바뀌기 전에 실패하면 아무 것도 반영되지
        // 않게 한다.
        if (param.name != null && param.name != project.name) {
            val owner = project.owner ?: ""
            if (projectRepository.existsByOwnerIgnoreCaseAndNameIgnoreCaseAndIdNot(owner, param.name, projectId)) {
                throw IllegalArgumentException("이미 사용 중인 프로젝트 이름입니다.")
            }

            val originalName = project.name
            recordRenameOrTransferHistoryIfLastChangePassed24HoursFrom(project, owner, originalName)

            // yona `repository.renameTo(updatedProject.name)`가 실패하면 FileOperationException을
            // 던져 저장을 막는다 — yona는 대응하는 체크 예외가 없어 IllegalStateException으로 이식.
            val repository = repositoryService.getRepository(project)
            if (!repository.renameTo(param.name)) {
                throw IllegalStateException("저장소 이름 변경에 실패했습니다: $owner/${param.name}")
            }

            project.name = param.name

            // 이 프로젝트를 즐겨찾기한 모든 사용자의 비정규화된 owner/projectName도 함께 최신화한다.
            favoriteProjectRepository.findByProjectId(project.id!!).forEach {
                it.owner = project.owner ?: ""
                it.projectName = project.name ?: ""
                favoriteProjectRepository.save(it)
            }
        }

        project.overview = param.overview
        project.projectScope = param.projectScope
        project.isCodeAccessibleMemberOnly = param.isCodeAccessibleMemberOnly
        project.isUsingReviewerCount = param.isUsingReviewerCount
        project.defaultReviewerCount = param.defaultReviewerCount
        
        project.isCodeEnabled = param.isCodeEnabled
        project.isIssueEnabled = param.isIssueEnabled
        project.isPullRequestEnabled = param.isPullRequestEnabled
        project.isReviewEnabled = param.isReviewEnabled
        project.isMilestoneEnabled = param.isMilestoneEnabled
        project.isBoardEnabled = param.isBoardEnabled
        project.isWikiEnabled = param.isWikiEnabled

        if (!param.defaultBranch.isNullOrBlank()) {
            try {
                val repository = repositoryService.getRepository(project)
                repository.setDefaultBranch("refs/heads/${param.defaultBranch}")
            } catch (e: Exception) {
                // 저장소 기본 브랜치 세팅 에러 방어
            }
        }

        return projectRepository.save(project)
    }

    @Transactional
    override fun deleteProject(projectId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }

        projectTransferRepository.deleteAll(projectTransferRepository.findByProjectId(projectId))

        // thread.project==이 프로젝트인 스레드를 지운다(reviewComments는 CommentThread 엔티티의
        // cascade=ALL, orphanRemoval=true로 함께 삭제됨). thread.project가 다른 프로젝트(fork가 제3
        // 프로젝트로 보낸 PR 등)인 스레드는 이 단계로는 안 잡히고, 아래 deletePullRequestCascade()가
        // PR 단위로 마저 정리한다.
        commentThreadRepository.deleteAll(commentThreadRepository.findByProject(project))

        // 이 프로젝트가 보낸(fromProject) PR과 받은(toProject) PR을 모두 지운다.
        (pullRequestRepository.findByFromProject(project) + pullRequestRepository.findByToProject(project))
            .forEach { deletePullRequestCascade(it) }

        // 이 프로젝트를 fork한 자식 프로젝트는 삭제하지 않고, 그 fork가 관여한 PR만 정리한 뒤
        // 원본 연결을 끊는다 — fork 프로젝트 자체나 그 이슈/게시글 등은 보존.
        project.forkingProjects.forEach { fork ->
            (pullRequestRepository.findByFromProject(fork) + pullRequestRepository.findByToProject(fork))
                .forEach { deletePullRequestCascade(it) }
            fork.originalProject = null
            projectRepository.save(fork)
        }

        // yona Project.delete():723-725 issues 루프 대응(댓글/이벤트/즐겨찾기/첨부파일/TitleHead까지
        // IssueServiceImpl.deleteIssueCascade()가 함께 정리).
        issueRepository.findByProject(project).forEach { issueService.deleteIssueCascade(it) }

        // yona Project.delete():727-729 IssueLabelCategory 삭제 대응(라벨 및 조인테이블까지 함께 정리).
        issueLabelCategoryRepository.findByProject(project).forEach { category ->
            issueLabelService.deleteCategory(category.id!!)
        }

        // yona Project.delete():731-733 assignees 루프 대응 — Issue.assignee의 cascade=ALL로 대부분
        // 이미 삭제되지만, 어떤 이슈에도 연결되지 않은 잔여 Assignee가 있을 경우를 대비한 방어적 정리.
        assigneeRepository.deleteAll(assigneeRepository.findByProjectId(projectId))

        // yona Project.delete():735-737 webhooks 루프 대응 — WebhookThread.webhook_id FK가
        // nullable=false라 웹훅을 지우기 전에 먼저 정리해야 한다.
        webhookRepository.findByProjectId(projectId).forEach { webhook ->
            webhookThreadRepository.deleteAll(webhookThreadRepository.findByWebhookId(webhook.id!!))
            webhookRepository.delete(webhook)
        }

        // yona Project.delete():739-741 posts 루프 대응 — 프로젝트 전체가 삭제되는 상황이라
        // 게시글 개수만큼 알림이 발행되지 않도록 deletePosting() 대신 알림을 발행하지 않는
        // deletePostingCascade()를 쓴다(legacy도 Project.delete()에서 posting.delete()를 직접
        // 호출할 뿐 알림 발행 경로를 타지 않는다).
        postingRepository.findByProject(project).forEach { postingService.deletePostingCascade(it) }

        // yona Project.delete():743-746 labels 루프 대응 — yona의 Label은 project 소유 필드가 없는
        // category+name 기반 공용 개체라 project_label 조인테이블 행은 Project 삭제 시 Hibernate가
        // 자동으로 정리한다(별도 unlink 호출 불필요).

        // 연관 멤버 삭제
        val members = projectUserRepository.findByProjectId(projectId)
        projectUserRepository.deleteAll(members)

        watchService.deleteAll(ResourceType.PROJECT, projectId.toString())

        // DB Project 행만 지우고 물리 bare 저장소 디렉터리를 남겨두면, 같은 owner/name으로
        // 재생성하거나 새로 fork를 시도할 때 이미 존재하는 디렉터리/파일과 충돌해
        // FileAlreadyExistsException이 500으로 튄다. changeVCS()와 동일한 패턴을 쓴다 — 물리
        // 저장소가 이미 없거나 삭제 중 오류가 나도 DB 정리 자체는 막지 않는다.
        try {
            repositoryService.getRepository(project).delete()
        } catch (e: Exception) {
            // 물리 저장소 삭제 실패는 DB 삭제를 막지 않는다(changeVCS()와 동일한 방어적 처리).
        }

        projectRepository.delete(project)
    }

    // pullRequest FK만으로 스레드를 찾아 thread.project 값과 무관하게 지운다 — 위쪽
    // deleteCommentThreads 단계(findByProject)는 project==이 프로젝트인 스레드만 지우므로, fork가
    // 제3 프로젝트로 보낸 PR이나 이 프로젝트 자신이 보낸 PR에 달린 스레드는 project 단위 정리로는
    // 잡히지 않는다.
    private fun deletePullRequestCascade(pullRequest: PullRequest) {
        commentThreadRepository.deleteAll(commentThreadRepository.findByPullRequest(pullRequest))
        pullRequestEventRepository.deleteAll(pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest))
        pullRequestCommitRepository.deleteAll(pullRequestCommitRepository.findByPullRequest(pullRequest))
        watchService.deleteAll(ResourceType.PULL_REQUEST, pullRequest.id.toString())
        pullRequestRepository.delete(pullRequest)
    }

    @Transactional
    override fun requestNewTransfer(projectId: Long, senderId: Long, destination: String): ProjectTransfer {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found") }
        val sender = userRepository.findById(senderId)
            .orElseThrow { IllegalArgumentException("Sender not found") }

        // destination 적격성 검증 (유저 로그인 ID 또는 조직 이름)
        val destUser = userRepository.findByLoginId(destination)
        val destOrg = projectRepository.findByOwner(destination) // 기존에 조직 등으로 존재하거나 owner로 식별 가능한지
        
        val key = (1..50).map { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() }.joinToString("")
        // 목적지에 이미 동명 프로젝트가 있으면 name-1, name-2...로 충돌이 없을 때까지 뒤에 숫자를 붙인다.
        val newProjName = resolveNewProjectName(destination, project.name)

        val existing = projectTransferRepository.findByProjectAndSenderAndDestination(project, sender, destination)
        return if (existing.isPresent) {
            val pt = existing.get()
            pt.requested = Instant.now()
            pt.confirmKey = key
            projectTransferRepository.save(pt)
        } else {
            val pt = ProjectTransfer(
                project = project,
                sender = sender,
                destination = destination,
                confirmKey = key,
                newProjectName = newProjName,
                requested = Instant.now()
            )
            projectTransferRepository.save(pt)
        }
    }

    private fun recordRenameOrTransferHistoryIfLastChangePassed24HoursFrom(
        project: Project,
        currentOwner: String,
        currentName: String
    ) {
        val lastChanged = project.previousNameChangedTime
        val isFirstOrPassed24Hours = lastChanged == null ||
            lastChanged.isBefore(Instant.now().minusSeconds(24 * 3600))
        if (isFirstOrPassed24Hours) {
            project.previousNameChangedTime = Instant.now()
            project.previousName = currentName
            project.previousOwnerLoginId = currentOwner
        }
    }

    private fun resolveNewProjectName(destination: String, name: String): String {
        if (!projectRepository.findByOwnerAndName(destination, name).isPresent) {
            return name
        }
        var i = 1
        while (true) {
            val candidate = "$name-$i"
            if (!projectRepository.findByOwnerAndName(destination, candidate).isPresent) {
                return candidate
            }
            i++
        }
    }

    @Transactional
    override fun acceptTransfer(transferId: Long, confirmKey: String, acceptorId: Long) {
        val limit = Instant.now().minusSeconds(86400) // 24시간 전 유효
        val pt = projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(transferId, false, limit)
            .orElseThrow { IllegalArgumentException("Invalid or expired transfer request") }

        if (pt.confirmKey != confirmKey) {
            throw IllegalArgumentException("Confirm key mismatch")
        }

        val acceptor = userRepository.findById(acceptorId)
            .orElseThrow { IllegalArgumentException("Acceptor not found") }
        if (!isAuthorizedToAcceptTransfer(pt.destination, acceptor)) {
            throw IllegalArgumentException("이 프로젝트 이전을 수락할 권한이 없습니다.")
        }

        val project = pt.project
        val originalOwner = project.owner ?: ""
        val originalName = project.name
        val newOwner = pt.destination
        val newName = pt.newProjectName
        val senderId = pt.sender.id!!

        // 마지막 이전/개명 기록으로부터 24시간이 지났을 때만(또는 최초일 때만) 예전 위치를 갱신한다
        // — 짧은 시간 내 연속 이전이 일어나도 "예전 위치" 포인터가 계속 최신으로만 덮어써지지
        // 않도록 방지한다.
        recordRenameOrTransferHistoryIfLastChangePassed24HoursFrom(project, originalOwner, originalName)

        // 물리 저장소 폴더명 이동 — SvnRepository/HgRepository는 접미사 없는 "$owner/$name" 경로를
        // 쓰므로, git만 ".git" 접미사를 붙여야 한다(그렇지 않으면 SVN/Mercurial 프로젝트는
        // sourceDir.exists()가 거짓이 되어 물리 이동이 조용히 no-op된다).
        val vcsUpper = project.vcs?.uppercase()
        val baseDir = when (vcsUpper) {
            "SUBVERSION", "SVN" -> svnBaseDir
            "MERCURIAL", "HG" -> hgBaseDir
            else -> gitBaseDir
        }
        val dirSuffix = when (vcsUpper) {
            "SUBVERSION", "SVN", "MERCURIAL", "HG" -> ""
            else -> ".git"
        }
        val sourceDir = File(baseDir, "$originalOwner/$originalName$dirSuffix")
        val targetDir = File(baseDir, "$newOwner/$newName$dirSuffix")
        if (sourceDir.exists()) {
            targetDir.parentFile.mkdirs()
            sourceDir.renameTo(targetDir)
        }

        // DB 메타데이터 변경 반영
        project.owner = newOwner
        project.name = newName
        // 목적지가 조직이면 그 조직으로, 개인이면 null로 명시적으로 갱신한다.
        project.organization = organizationRepository.findByName(newOwner).orElse(null)
        projectRepository.save(project)

        // 이 프로젝트를 즐겨찾기한 모든 사용자의 비정규화된 owner/projectName도 함께 갱신한다 —
        // 그렇지 않으면 즐겨찾기 표시가 이관 후에도 옛 owner/projectName으로 남는다.
        favoriteProjectRepository.findByProjectId(project.id!!).forEach {
            it.owner = project.owner ?: ""
            it.projectName = project.name ?: ""
            favoriteProjectRepository.save(it)
        }

        // 권한(Role) 변경 처리
        // 1. 보낸 사람이 MANAGER였다면 MEMBER로 강등
        val senderProjectUser = projectUserRepository.findByProjectIdAndUserId(project.id!!, senderId).orElse(null)
        if (senderProjectUser != null && senderProjectUser.role.id == RoleType.MANAGER.roleType) {
            val memberRole = roleRepository.findById(RoleType.MEMBER.roleType)
                .orElseThrow { IllegalStateException("MEMBER role not found") }
            senderProjectUser.role = memberRole
            projectUserRepository.save(senderProjectUser)
        }

        // 2. 이관 목적지(destination) 사용자가 존재한다면 MANAGER 권한 부여
        val newOwnerUser = userRepository.findByLoginId(newOwner).orElse(null)
        if (newOwnerUser != null) {
            val newOwnerProjectUser = projectUserRepository.findByProjectIdAndUserId(project.id!!, newOwnerUser.id!!).orElse(null)
            val managerRole = roleRepository.findById(RoleType.MANAGER.roleType)
                .orElseThrow { IllegalStateException("MANAGER role not found") }
            if (newOwnerProjectUser != null) {
                newOwnerProjectUser.role = managerRole
                projectUserRepository.save(newOwnerProjectUser)
            } else {
                val projectUser = ProjectUser(
                    project = project,
                    user = newOwnerUser,
                    role = managerRole
                )
                projectUserRepository.save(projectUser)
            }
        }

        // 완료된 이관 요청은 accepted=true로 남겨두지 않고 DB에서 삭제한다.
        pt.accepted = true
        projectTransferRepository.delete(pt)
    }

    private fun isAuthorizedToAcceptTransfer(destination: String, acceptor: User): Boolean {
        if (acceptor.loginId == destination) {
            return true
        }
        val organization = organizationRepository.findByName(destination).orElse(null) ?: return false
        val orgUser = organizationUserRepository
            .findByOrganizationIdAndUserId(organization.id!!, acceptor.id!!)
            .orElse(null) ?: return false
        return orgUser.role.id == RoleType.ORG_ADMIN.roleType
    }

    // 이 메서드는 DB 저장 다음에 Files.createLink()가 체크 예외(FileAlreadyExistsException/
    // IOException)로 실패할 수 있는데, Spring 트랜잭션의 기본 롤백 규칙은 RuntimeException/Error만
    // 대상으로 삼아 체크 예외는 커밋 대상으로 취급한다 — 그러면 파일시스템 작업이 실패해도 이미
    // 실행된 DB 저장은 커밋되어 owner+name이 중복된 Project 행이 남는다. rollbackFor =
    // [Exception::class]로 체크 예외도 롤백 대상에 포함시켜 부분 커밋을 막는다.
    @Transactional(rollbackFor = [Exception::class])
    override fun forkProject(
        projectId: Long,
        forkerId: Long,
        destinationOwner: String,
        destinationName: String
    ): Project {
        val original = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Original project not found") }
        val forker = userRepository.findById(forkerId)
            .orElseThrow { IllegalArgumentException("Forker user not found") }

        val destOwner = if (destinationOwner.isNotBlank()) destinationOwner else forker.loginId
        val destName = if (destinationName.isNotBlank()) destinationName else original.name

        // destinationOwner가 임의의 문자열이면(호출자가 폼/REST 바디로 직접 지정) 이름 충돌 검사 전에
        // 먼저 forker가 그 이름으로 fork할 권한이 있는지 확인한다 — 이 검증이 없으면 아무 로그인
        // 사용자나 자신이 속하지 않은 조직(또는 다른 사용자)의 이름을 destinationOwner로 지정해
        // 그 네임스페이스에 프로젝트를 만들고 스스로 MANAGER가 될 수 있었다(그 이름에 아직 프로젝트가
        // 없기만 하면 충돌 검사를 통과함). acceptTransfer()와 동일한 규칙(본인 계정이거나 ORG_ADMIN인
        // 조직만 허용)을 재사용한다.
        if (!isAuthorizedToAcceptTransfer(destOwner, forker)) {
            throw IllegalArgumentException("'$destOwner' 이름으로 포크할 권한이 없습니다 — 본인 계정이거나 관리자(ORG_ADMIN)로 속한 조직만 목적지로 지정할 수 있습니다.")
        }

        // 목적지가 이미 존재하면 파일시스템 하드링크를 시도하기도 전에 400 계열로 거절한다 —
        // 예측 가능한 충돌이므로 트랜잭션 롤백에 기대는 대신 사전 검증으로 막는 게 더 저렴하다.
        if (projectRepository.findByOwnerAndName(destOwner, destName).isPresent) {
            throw IllegalArgumentException("'$destOwner/$destName' 프로젝트가 이미 존재합니다.")
        }

        // 포크 대상 껍데기 프로젝트 엔티티 복제 생성
        val forked = Project(
            name = destName,
            overview = original.overview,
            vcs = original.vcs,
            siteurl = "http://localhost:9000/$destName",
            owner = destOwner,
            projectScope = original.projectScope,
            originalProject = original
        )
        val savedFork = projectRepository.save(forked)

        // 포크 유저를 자식 프로젝트의 MANAGER 멤버로 매핑
        val managerRole = roleRepository.findById(RoleType.MANAGER.roleType)
            .orElseThrow { IllegalStateException("MANAGER role not found") }
        val projectUser = ProjectUser(
            project = savedFork,
            user = forker,
            role = managerRole
        )
        projectUserRepository.save(projectUser)

        // 물리 저장소를 하드링크(Hard Link) 방식으로 무복사 복제 — cloneHardLinkedRepository() 자체는
        // 디렉터리를 재귀적으로 훑어 모든 파일을 하드링크하는 범용 구현이라 git 전용이 아니다
        // (SVN/Mercurial 저장소 디렉터리 구조에도 그대로 적용 가능). 다만 아래 sourceDir/targetDir이
        // 1라운드까지는 vcs 종류와 무관하게 항상 ".git" 접미사를 붙이고 있어(acceptTransfer()와 동일한
        // 결함), SvnRepository/HgRepository의 접미사 없는 실제 경로와 어긋나 SVN/Mercurial 프로젝트는
        // sourceDir.exists()가 거짓이 되어 포크 시 물리 복제가 조용히 no-op됐다 — 2라운드에서 수정.
        val vcsUpper = original.vcs?.uppercase()
        val baseDir = when (vcsUpper) {
            "SUBVERSION", "SVN" -> svnBaseDir
            "MERCURIAL", "HG" -> hgBaseDir
            else -> gitBaseDir
        }
        val dirSuffix = when (vcsUpper) {
            "SUBVERSION", "SVN", "MERCURIAL", "HG" -> ""
            else -> ".git"
        }
        val sourceDir = File(baseDir, "${original.owner}/${original.name}$dirSuffix")
        val targetDir = File(baseDir, "$destOwner/$destName$dirSuffix")

        if (sourceDir.exists()) {
            try {
                cloneHardLinkedRepository(sourceDir, targetDir)
            } catch (e: IOException) {
                // 위 findByOwnerAndName() 사전 체크와 실제 하드링크 사이에는 시간차가 있다(TOCTOU)
                // — 동시에 두 번 fork하면 나중 요청이 FileAlreadyExistsException으로 실패한다. 이
                // 메서드는 @Transactional이라 예외가 전파되면 방금 저장한 row는 롤백되므로, 순차
                // 중복 fork와 동일한 메시지로 통일해 500 대신 400으로 거절한다.
                throw IllegalArgumentException("'$destOwner/$destName' 프로젝트가 이미 존재합니다.")
            }
        }

        return savedFork
    }

    /**
     * 원본 저장소를 하드링크 기반으로 무복사 복제합니다.
     * 
     * [제약 사항 및 한계 상황]:
     * - 이 기능은 파일 시스템 수준의 하드링크(Hard Link)를 생성하므로, 원본 디렉토리와 대상 디렉토리가
     *   물리적으로 동일한 디스크 파티션/볼륨에 위치해야만 정상 작동합니다.
     * - 서로 다른 볼륨(Cross-device) 간 포크 시에는 java.nio.file.FileSystemException (EXDEV)이 발생하게 되며,
     *   이 예외에 대한 별도의 런타임 물리 복사(Files.copy) 폴백 처리는 의도적으로 생략되었습니다.
     */
    private fun cloneHardLinkedRepository(source: File, target: File) {
        if (!target.exists()) {
            target.mkdirs()
        }
        source.listFiles()?.forEach { file ->
            val targetFile = File(target, file.name)
            if (file.isDirectory) {
                cloneHardLinkedRepository(file, targetFile)
            } else {
                Files.createLink(targetFile.toPath(), file.toPath())
            }
        }
    }

    @Transactional
    override fun changeVCS(projectId: Long): Project {
        val project = projectRepository.findById(projectId).orElseThrow { IllegalArgumentException("Project not found") }

        for (fork in project.forkingProjects) {
            fork.originalProject = null
            projectRepository.save(fork)
        }
        project.forkingProjects.clear()

        try {
            repositoryService.getRepository(project).delete()
        } catch (e: Exception) {
            // ignore
        }

        project.vcs = nextVcsInCycle(project.vcs)

        repositoryService.getRepository(project).create()

        return projectRepository.save(project)
    }

    override fun getProjectLabels(projectId: Long): Set<Label> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        return project.labels
    }

    @Transactional
    override fun attachLabel(projectId: Long, category: String?, name: String): AttachLabelResult {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        val resolvedCategory = category ?: "Label"

        var label = labelRepository.findByCategoryAndName(resolvedCategory, name).orElse(null)
        val isCreated = label == null
        if (label == null) {
            label = labelRepository.save(Label(category = resolvedCategory, name = name))
        }

        if (project.labels.any { it.id == label.id }) {
            // yona Project.attachLabel(): 이미 붙어있으면 아무 것도 하지 않고 false를 반환한다.
            return AttachLabelResult(label, isCreated, isAttached = false)
        }

        project.labels.add(label)
        projectRepository.save(project)
        return AttachLabelResult(label, isCreated, isAttached = true)
    }

    @Transactional
    override fun detachLabel(projectId: Long, labelId: Long): Boolean {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        val label = labelRepository.findById(labelId).orElse(null) ?: return false

        project.labels.remove(label)
        projectRepository.save(project)

        // yona Label.delete(project) 이후 Project.detachLabel(): 라벨을 참조하는 프로젝트가
        // 더 이상 없으면(0개) 이 전역 라벨 자체를 삭제한다.
        if (projectRepository.countByLabelsId(labelId) == 0L) {
            labelRepository.delete(label)
        }
        return true
    }
}

