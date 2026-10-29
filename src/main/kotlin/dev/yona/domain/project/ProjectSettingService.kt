package dev.yona.domain.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.misc.FavoriteProjectRepository
import dev.yona.repository.RepositoryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream

/**
 * 프로젝트 설정 폼의 입력값이다. 값은 요청에서 온 그대로(검증 전) 담는다. 보내지 않은 항목은 `null`이다.
 *
 * v1.6은 요청 파라미터를 `Project`에 직접 묶어서 폼이 보내지 않는 항목(`owner`, `vcs` 등)도 요청으로 바꿀 수 있었다.
 * v2는 아래 항목만 받는다([decisions.md] A-24).
 *
 * @property name 프로젝트 이름
 * @property overview 설명
 * @property projectScope 공개 범위 이름
 * @property isCodeAccessibleMemberOnly 코드를 구성원에게만 보일지 (`true`/`false` 문자열)
 * @property isUsingReviewerCount 프로젝트 전체 기본 리뷰어 수를 쓸지 (`true`/`false` 문자열, `decisions.md` D-43). 켜면 보호 규칙이 없는 브랜치에 적용된다
 * @property defaultReviewerCount 프로젝트 전체 기본 리뷰어 수
 * @property isDismissingStaleApprovals 새 커밋이 올라오면 오래된 승인을 해제할지 (`true`/`false` 문자열, 추가 개발 항목 X-12, `decisions.md` D-35 5번)
 * @property allowMergeCommit 병합 커밋 방식을 허용할지 (`true`/`false` 문자열, 추가 개발 항목 X-28, `decisions.md` D-41)
 * @property allowSquashMerge 스쿼시 방식을 허용할지
 * @property allowRebaseMerge 리베이스 방식을 허용할지
 * @property defaultMergeMethod 기본 병합 방식 이름(`merge`, `squash`, `rebase`). 허용한 방식이어야 한다
 * @property defaultBranch 기본 브랜치 참조 이름 (예: `refs/heads/master`)
 * @property menus 켜기로 한 메뉴 이름 모음. 체크하지 않은 메뉴는 꺼진다
 */
data class ProjectSettingForm(
    val name: String? = null,
    val overview: String? = null,
    val projectScope: String? = null,
    val isCodeAccessibleMemberOnly: String? = null,
    val isUsingReviewerCount: String? = null,
    val defaultReviewerCount: String? = null,
    val isDismissingStaleApprovals: String? = null,
    val allowMergeCommit: String? = null,
    val allowSquashMerge: String? = null,
    val allowRebaseMerge: String? = null,
    val defaultMergeMethod: String? = null,
    val defaultBranch: String? = null,
    val menus: Set<String> = emptySet()
)

/**
 * 업로드한 로고 파일이다.
 *
 * @property filename 원래 파일 이름. 파일을 고르지 않았으면 비어 있다
 * @property size 크기(바이트)
 * @property open 내용을 읽는 스트림을 연다
 */
class LogoUpload(val filename: String?, val size: Long, val open: () -> InputStream) {
    /** 파일을 고르지 않았으면 `true`이다 (v1.6 `LogoUtil.isEmptyFilePart`). */
    val isEmpty: Boolean get() = filename.isNullOrEmpty()
}

/**
 * 설정 검증 결과이다.
 *
 * @property errors 필드별 오류
 * @property warning 화면에 알릴 안내 메시지 키. v1.6의 `flash("warning", ...)`이며 마지막으로 정한 값이 남는다
 */
class SettingValidation(val errors: FormErrors, val warning: String?) {
    fun hasErrors(): Boolean = errors.hasErrors()
}

/**
 * 프로젝트 설정의 검증과 저장이다. v1.6 `ProjectApp.settingProject`, `validateWhenUpdate`,
 * `saveProjectMenuSetting`(`app/controllers/ProjectApp.java:235-330`)에 해당한다.
 */
@Service
class ProjectSettingService(
    private val projects: ProjectRepository,
    private val menuSettings: ProjectMenuSettingRepository,
    private val favorites: FavoriteProjectRepository,
    private val attachments: AttachmentService,
    private val repositories: RepositoryService
) {

    /**
     * 입력값을 검증한다. v1.6와 같은 순서로 안내를 정하므로 나중에 정한 안내가 앞의 안내를 덮는다.
     *
     * 1. 같은 소유자에 같은 이름(대소문자 무시)이 이미 있으면(자기 자신은 제외) `project.name.duplicate`
     * 2. 로고가 있으면 이미지 확장자(`jpg`, `jpeg`, `png`, `gif`, `bmp`)가 아니거나 5MB를 넘을 때
     * 3. 이름 규칙 위반(형식, 예약어)이 첫 오류이면 그 오류를 안내 문구로 바꾼다
     */
    fun validate(project: Project, form: ProjectSettingForm, logo: LogoUpload?): SettingValidation {
        val errors = FormErrors()
        var warning: String? = null

        // 입력 묶기 단계의 검사
        ProjectNameRules.violations(form.name).forEach { errors.reject("name", it) }
        if (form.projectScope != null && scopeOf(form.projectScope) == null) errors.reject("projectScope", "error.invalid")
        form.isCodeAccessibleMemberOnly?.let { if (booleanOf(it) == null) errors.reject("isCodeAccessibleMemberOnly", "error.invalid") }
        form.isUsingReviewerCount?.let { if (booleanOf(it) == null) errors.reject("isUsingReviewerCount", "error.invalid") }
        form.isDismissingStaleApprovals?.let { if (booleanOf(it) == null) errors.reject("isDismissingStaleApprovals", "error.invalid") }
        form.defaultReviewerCount?.let { if (it.toIntOrNull() == null) errors.reject("defaultReviewerCount", "error.invalid") }
        validateMergeMethods(project, form, errors)

        // 폼 검증 단계 (validateWhenUpdate)
        val name = form.name
        if (name != null && !isNameChangeable(project, name)) {
            warning = "project.name.duplicate"
            errors.reject("name", "project.name.duplicate")
        }

        if (logo != null && !logo.isEmpty) {
            if (!isImageFile(requireNotNull(logo.filename))) {
                warning = "project.logo.alert"
                errors.reject("logoPath", "project.logo.alert")
            } else if (logo.size > LOGO_FILE_LIMIT_SIZE) {
                warning = "project.logo.fileSizeAlert"
                errors.reject("logoPath", "project.logo.fileSizeAlert")
            }
        }

        errors.first("name")?.let { first ->
            val message = ProjectNameRules.toUserMessageKey(first)
            if (message != first) {
                warning = message
                errors.clear("name")
                errors.reject("name", message)
            }
        }
        return SettingValidation(errors, warning)
    }

    /**
     * 병합 방식 설정을 검사한다(추가 개발 항목 X-28, `decisions.md` D-41). 모르는 값은 거절하고, 보내지 않은 항목은 지금 값으로 채워 본 결과가 허용하는 방식이 하나 이상이고
     * 기본 방식이 그 안에 있어야 한다. 이 항목을 하나도 보내지 않으면 검사하지 않는다.
     */
    private fun validateMergeMethods(project: Project, form: ProjectSettingForm, errors: FormErrors) {
        val flags = listOf("allowMergeCommit" to form.allowMergeCommit, "allowSquashMerge" to form.allowSquashMerge, "allowRebaseMerge" to form.allowRebaseMerge)
        flags.forEach { (name, value) -> value?.let { if (booleanOf(it) == null) errors.reject(name, "error.invalid") } }
        form.defaultMergeMethod?.let { if (dev.yona.domain.pullrequest.MergeMethod.of(it) == null) errors.reject("defaultMergeMethod", "error.invalid") }
        if (errors.hasErrors() || (flags.all { it.second == null } && form.defaultMergeMethod == null)) return
        val merge = form.allowMergeCommit?.let { booleanOf(it) } ?: (project.allowMergeCommit != false)
        val squash = form.allowSquashMerge?.let { booleanOf(it) } ?: (project.allowSquashMerge == true)
        val rebase = form.allowRebaseMerge?.let { booleanOf(it) } ?: (project.allowRebaseMerge == true)
        if (!merge && !squash && !rebase) { errors.reject("allowMergeCommit", "error.invalid"); return }
        val default = dev.yona.domain.pullrequest.MergeMethod.of(form.defaultMergeMethod ?: project.defaultMergeMethod) ?: dev.yona.domain.pullrequest.MergeMethod.MERGE
        val allowed = setOfNotNull(
            dev.yona.domain.pullrequest.MergeMethod.MERGE.takeIf { merge }, dev.yona.domain.pullrequest.MergeMethod.SQUASH.takeIf { squash }, dev.yona.domain.pullrequest.MergeMethod.REBASE.takeIf { rebase }
        )
        if (default !in allowed) errors.reject("defaultMergeMethod", "error.invalid")
    }

    /**
     * 설정을 저장한다. 검증을 통과한 입력만 받는다.
     *
     * 순서는 v1.6과 같다. 로고 교체 -> 기본 브랜치 -> 이름 변경(이전 위치 기록, 저장소 디렉터리 이동) -> 필드 저장 -> 메뉴 설정 ->
     * 즐겨찾기 갱신. 저장소 디렉터리를 옮기지 못하면 예외를 던지고 트랜잭션은 취소된다.
     *
     * @param ownerLoginId 로고를 올린 사용자의 로그인 ID
     */
    @Transactional
    fun update(project: Project, form: ProjectSettingForm, logo: LogoUpload?, ownerLoginId: String?) {
        val projectId = requireNotNull(project.id)

        if (logo != null && !logo.isEmpty) {
            attachments.deleteAll(ResourceType.PROJECT, projectId)
            attachments.store(logo.open(), logo.filename, ResourceType.PROJECT, projectId, ownerLoginId)
        }

        if (!form.defaultBranch.isNullOrEmpty()) repositories.setDefaultBranch(project, form.defaultBranch)

        val newName = requireNotNull(form.name)
        if (project.name != newName) {
            project.recordPreviousPlaceIfLastChangePassed24Hours()
            if (!repositories.renameRepository(project, newName)) {
                throw IllegalStateException("fail repository rename to ${project.owner}/$newName")
            }
            project.name = newName
        }

        form.overview?.let { project.overview = it }
        scopeOf(form.projectScope)?.let { project.projectScope = it }
        form.isCodeAccessibleMemberOnly?.let { project.isCodeAccessibleMemberOnly = booleanOf(it) }
        form.isUsingReviewerCount?.let { project.isUsingReviewerCount = booleanOf(it) }
        form.isDismissingStaleApprovals?.let { project.isDismissingStaleApprovals = booleanOf(it) }
        form.defaultReviewerCount?.let { project.defaultReviewerCount = it.toInt() }
        form.allowMergeCommit?.let { project.allowMergeCommit = booleanOf(it) }
        form.allowSquashMerge?.let { project.allowSquashMerge = booleanOf(it) }
        form.allowRebaseMerge?.let { project.allowRebaseMerge = booleanOf(it) }
        form.defaultMergeMethod?.let { project.defaultMergeMethod = it }
        projects.save(project)

        saveMenuSetting(project, form.menus)
        updateFavorites(project)
    }

    private fun saveMenuSetting(project: Project, menus: Set<String>) {
        val setting = menuSettings.findFirstByProjectId(requireNotNull(project.id))
            ?: ProjectMenuSetting(project = project)
        setting.code = "code" in menus
        setting.issue = "issue" in menus
        setting.pullRequest = "pullRequest" in menus
        setting.review = "review" in menus
        setting.milestone = "milestone" in menus
        setting.board = "board" in menus
        menuSettings.save(setting)
    }

    private fun updateFavorites(project: Project) {
        favorites.findByProjectId(requireNotNull(project.id)).forEach {
            it.owner = project.owner
            it.projectName = project.name
            favorites.save(it)
        }
    }

    private fun isNameChangeable(project: Project, name: String): Boolean {
        val same = projects.findByOwnerIgnoreCaseAndNameIgnoreCase(project.owner.orEmpty(), name)
        return same == null || same.id == project.id
    }

    private fun isImageFile(filename: String): Boolean = LOGO_TYPES.any { filename.lowercase().endsWith(it) }

    private fun scopeOf(value: String?): ProjectScope? = value?.let { v -> ProjectScope.entries.firstOrNull { it.name == v } }

    /** Play의 불리언 묶기와 같이 `true`/`on`/`yes`/`1`은 참, `false`/`off`/`no`/`0`은 거짓이고 나머지는 잘못된 값이다. */
    private fun booleanOf(value: String): Boolean? = when (value.lowercase()) {
        "true", "on", "yes", "1" -> true
        "false", "off", "no", "0" -> false
        else -> null
    }

    companion object {
        /** 로고 파일의 최대 크기(바이트)이다 (v1.6 `LogoUtil.LOGO_FILE_LIMIT_SIZE`, 5M). */
        const val LOGO_FILE_LIMIT_SIZE = 1024 * 1000 * 5

        /** 로고로 쓸 수 있는 확장자이다 (v1.6 `LogoUtil.LOGO_TYPE`). */
        private val LOGO_TYPES = listOf("jpg", "jpeg", "png", "gif", "bmp")

    }
}
