package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranch
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

// yona-wiki P3-04(브랜치 보호) 2라운드 — 1라운드에서 `ProtectedBranch` 엔티티/리포지토리와 강제
// 로직(GitPushHooks.BranchProtectionPreReceiveHook, PullRequestServiceImpl.
// checkBranchProtectionForMerge())까지는 구현했지만, 프로젝트 소유자/매니저가 실제로 규칙을
// 만들거나 조회·수정·삭제할 진입점이 전혀 없었다(DB 직접 조작 말고는 방법이 없던 갭). 같은 성격의
// 기존 기능(프로젝트 범위 설정, 매니저 전용 관리 화면)인 `WebhookController`와 동일한 패턴 —
// 프로젝트 조회 + `AccessControl.isAllowed(user, project, Operation.UPDATE)` 권한 체크 — 을 그대로
// 재사용한다. `ProtectedBranch`는 별도 서비스 레이어 없이(GitPushHooks/PullRequestServiceImpl도
// 동일하게 리포지토리를 직접 사용) 리포지토리를 직접 호출한다 — 엔티티에 비즈니스 로직이 없어
// 서비스 레이어를 추가하면 과도한 설계가 된다.
//
// 폼 파라미터 명명은 "모호하면 GitHub 방식을 기본값으로" 방침에 따라 GitHub의
// Settings > Branches > Branch protection rules 화면 문구를 그대로 차용한다. GitHub는
// allowForcePush/allowDeletions/doNotAllowBypassing을 긍정문(허용/금지)으로 노출하는데
// `ProtectedBranch` 엔티티는 부정형(disallowForcePush/disallowDelete/adminsCanBypass)으로
// 저장하므로, 폼 파라미터 → 엔티티 필드 변환 시 의미를 뒤집는다(아래 각 메서드 참고).
@Controller
class BranchProtectionController(
    private val protectedBranchRepository: ProtectedBranchRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkPermission(project: Project, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.UPDATE)
    }

    private fun findProjectOrThrow(owner: String, projectName: String): Project {
        return projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")
    }

    @GetMapping("/projects/{owner}/{projectName}/branch-protections")
    fun branchProtections(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = findProjectOrThrow(owner, projectName)

        val user = getLoginUser(authentication)
        if (!checkPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        }

        val branchProtections = protectedBranchRepository.findByProjectId(project.id ?: 0L)
        model.addAttribute("project", project)
        model.addAttribute("branchProtections", branchProtections)
        return "project/setting_branch_protection"
    }

    // branchPattern 컬럼 길이(250)/restrictPushTo 컬럼 길이(1000) 제약은 WebhookController의
    // payloadUrl/secret 사전 검증(P2-28)과 동일한 이유로 컨트롤러에서 미리 걸러낸다 — 이 검증이
    // 없으면 DB에 닿기도 전에 400을 반환해야 할 입력이 그대로 저장을 시도하다 컬럼 길이 제약
    // 위반(처리되지 않은 500)으로 노출될 수 있다.
    @PostMapping("/projects/{owner}/{projectName}/branch-protections")
    fun newBranchProtection(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        @RequestParam("branchPattern") branchPattern: String,
        @RequestParam(value = "requirePullRequest", defaultValue = "false") requirePullRequest: Boolean,
        @RequestParam(value = "requireApprovals", defaultValue = "0") requireApprovals: Int,
        @RequestParam(value = "requireSignedCommits", defaultValue = "false") requireSignedCommits: Boolean,
        @RequestParam(value = "restrictPushTo", required = false) restrictPushTo: String?,
        @RequestParam(value = "allowForcePush", defaultValue = "false") allowForcePush: Boolean,
        @RequestParam(value = "allowDeletions", defaultValue = "false") allowDeletions: Boolean,
        @RequestParam(value = "doNotAllowBypassing", defaultValue = "false") doNotAllowBypassing: Boolean,
        authentication: Authentication?
    ): String {
        val project = findProjectOrThrow(owner, projectName)

        val user = getLoginUser(authentication)
        if (!checkPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        }

        validateBranchPattern(branchPattern)
        validateRestrictPushTo(restrictPushTo)

        protectedBranchRepository.save(
            ProtectedBranch(
                project = project,
                branchPattern = branchPattern,
                requirePullRequest = requirePullRequest,
                requireApprovals = requireApprovals,
                requireSignedCommits = requireSignedCommits,
                restrictPushTo = restrictPushTo,
                disallowForcePush = !allowForcePush,
                disallowDelete = !allowDeletions,
                adminsCanBypass = !doNotAllowBypassing
            )
        )

        return "redirect:/projects/$owner/$projectName/branch-protections"
    }

    @PostMapping("/projects/{owner}/{projectName}/branch-protections/{id}")
    fun updateBranchProtection(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        @PathVariable("id") id: Long,
        @RequestParam("branchPattern") branchPattern: String,
        @RequestParam(value = "requirePullRequest", defaultValue = "false") requirePullRequest: Boolean,
        @RequestParam(value = "requireApprovals", defaultValue = "0") requireApprovals: Int,
        @RequestParam(value = "requireSignedCommits", defaultValue = "false") requireSignedCommits: Boolean,
        @RequestParam(value = "restrictPushTo", required = false) restrictPushTo: String?,
        @RequestParam(value = "allowForcePush", defaultValue = "false") allowForcePush: Boolean,
        @RequestParam(value = "allowDeletions", defaultValue = "false") allowDeletions: Boolean,
        @RequestParam(value = "doNotAllowBypassing", defaultValue = "false") doNotAllowBypassing: Boolean,
        authentication: Authentication?
    ): String {
        val project = findProjectOrThrow(owner, projectName)

        val user = getLoginUser(authentication)
        if (!checkPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        }

        val rule = findRuleOfProjectOrThrow(project, id)

        validateBranchPattern(branchPattern)
        validateRestrictPushTo(restrictPushTo)

        rule.branchPattern = branchPattern
        rule.requirePullRequest = requirePullRequest
        rule.requireApprovals = requireApprovals
        rule.requireSignedCommits = requireSignedCommits
        rule.restrictPushTo = restrictPushTo
        rule.disallowForcePush = !allowForcePush
        rule.disallowDelete = !allowDeletions
        rule.adminsCanBypass = !doNotAllowBypassing
        protectedBranchRepository.save(rule)

        return "redirect:/projects/$owner/$projectName/branch-protections"
    }

    @DeleteMapping("/projects/{owner}/{projectName}/branch-protections/{id}")
    @ResponseBody
    fun deleteBranchProtection(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        @PathVariable("id") id: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val user = getLoginUser(authentication)
        if (!checkPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val rule = protectedBranchRepository.findById(id).orElse(null)
        if (rule == null || rule.project?.id != project.id) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }

        protectedBranchRepository.deleteById(id)
        return ResponseEntity.ok().build()
    }

    // id로 조회한 규칙이 실제로 이 프로젝트 소유인지 확인한다 — 확인하지 않으면 다른 프로젝트의
    // 매니저가 임의의 id를 넣어 타 프로젝트 브랜치 보호 규칙을 수정/삭제할 수 있는 IDOR이 된다.
    private fun findRuleOfProjectOrThrow(project: Project, id: Long): ProtectedBranch {
        val rule = protectedBranchRepository.findById(id).orElse(null)
        if (rule == null || rule.project?.id != project.id) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Branch protection rule not found")
        }
        return rule
    }

    private fun validateBranchPattern(branchPattern: String) {
        if (branchPattern.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "브랜치 이름 패턴은 필수 입력 항목입니다.")
        }
        if (branchPattern.length > 250) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "입력한 브랜치 이름 패턴이 너무 깁니다. (최대 250자)")
        }
    }

    private fun validateRestrictPushTo(restrictPushTo: String?) {
        if (!restrictPushTo.isNullOrEmpty() && restrictPushTo.length > 1000) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "입력한 사용자 목록이 너무 깁니다. (최대 1000자)")
        }
    }
}
