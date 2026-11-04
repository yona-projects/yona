package dev.yona.domain.project

import dev.yona.util.LegacyText
import dev.yona.access.AccessControl
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.internal.JGitText
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.MessageFormat
import java.util.Date

/**
 * 가져오기 폼의 값이다.
 *
 * @property url 가져올 Git 저장소 주소
 * @property authId 저장소에 접근할 아이디. 어디에도 저장하지 않는다
 */
data class ProjectImportForm(
    val owner: String?, val name: String?, val overview: String?, val projectScope: String?, val url: String?, val authId: String?, val authPw: String?, val menus: Set<String>
)

/** 가져오기 결과이다. */
sealed class ImportResult {
    /** 만들었다. */
    data class Created(val project: Project) : ImportResult()

    /** 입력이 올바르지 않거나 저장소를 가져오지 못했다. [errors]를 폼에 되돌려 준다. */
    data class Rejected(val errors: FormErrors) : ImportResult()

    /** 조직의 관리자가 아니라서 만들 수 없다. */
    object Forbidden : ImportResult()
}

/**
 * 다른 Git 저장소를 가져와 새 프로젝트를 만든다. v1.6 `ImportApp.newProject`와 `validateForm`(`app/controllers/ImportApp.java:52-216`)에 해당한다.
 *
 * 검증 순서와 오류는 v1.6과 같다: 소유자가 사용자도 조직도 아니거나 다른 사용자이면 `project.owner.invalidate`, 같은 이름이 있으면 `project.name.duplicate`,
 * 주소가 비면 `project.import.error.empty.url`이다. 앞에서 오류가 하나라도 있으면(이름 규칙을 어긴 경우 포함) 이름에 `project.name.alert`를 더 붙인다.
 * 소유자가 조직이면 그 조직의 관리자여야 한다.
 *
 * 저장소를 먼저 복제하고 나서 프로젝트를 만든다. 주소가 틀렸거나 저장소가 아니면 `project.import.error.wrong.url`, 접근 권한이 없으면
 * (인증 정보가 없으면 인증이 필요하다는 안내와 함께) `…transport.unauthorized`, 인증 정보가 틀렸으면 `…failedToAuth`, 접근이 금지되면 `…forbidden`,
 * 그 밖의 전송 오류는 응답 코드를 담은 `…transport`이다. 실패하면 복제하다 만 저장소를 지운다.
 * 만든 사람은 소유자가 사용자일 때만 매니저가 된다(조직 소유이면 매니저를 두지 않는다, [refactoring-backlog.md] R-46). 구독이나 방문 기록은 남기지 않는다.
 */
@Service
class ProjectImportService(
    private val accessControl: AccessControl,
    private val users: UserRepository,
    private val projects: ProjectRepository,
    private val organizations: OrganizationRepository,
    private val organizationUsers: OrganizationUserRepository,
    private val projectUsers: ProjectUserRepository,
    private val menuSettings: ProjectMenuSettingRepository,
    private val roles: RoleRepository,
    private val repositories: RepositoryService,
    private val messages: org.springframework.context.MessageSource,
    private val audit: dev.yona.domain.audit.AuditRecorder
) {

    @Transactional
    fun import(form: ProjectImportForm, creator: User): ImportResult {
        val errors = FormErrors()
        val owner = form.owner
        val name = form.name
        val ownerUser = owner?.let { users.findByLoginId(it) }
        val organization = owner?.let { organizations.findByNameIgnoreCase(it) }
        val ownerIsUser = owner?.let { users.existsByLoginIdIgnoreCase(it) } == true

        // 입력 묶기 단계의 오류: 이름 규칙과 공개 범위
        ProjectNameRules.violations(name).forEach { errors.reject("name", it) }
        val scope = form.projectScope?.let { value -> dev.yona.domain.enumeration.ProjectScope.entries.firstOrNull { it.name == value } }
        if (form.projectScope != null && scope == null) errors.reject("projectScope", "error.invalid")

        if (!ownerIsUser && organization == null) errors.reject("owner", "project.owner.invalidate")
        if (ownerIsUser && ownerUser?.id != creator.id) errors.reject("owner", "project.owner.invalidate")
        if (organization != null && !organizationUsers.existsByOrganizationIdAndUserIdAndRoleId(requireNotNull(organization.id), requireNotNull(creator.id), RoleType.ORG_ADMIN.id)) {
            return ImportResult.Forbidden
        }
        if (owner != null && name != null && projects.existsByOwnerIgnoreCaseAndNameIgnoreCase(owner, name)) errors.reject("name", "project.name.duplicate")
        if (LegacyText.isBlank(form.url)) errors.reject("url", "project.import.error.empty.url")
        if (errors.hasErrors()) {
            // v1.6은 어떤 오류가 있어도 이름 규칙 안내를 더 붙였다(R-46-5). 이름 규칙을 어긴 경우에만 붙인다
            if (ProjectNameRules.violations(name).isNotEmpty()) errors.reject("name", "project.name.alert")
            return ImportResult.Rejected(errors)
        }

        val project = Project(
            owner = owner, name = name, overview = form.overview, vcs = "GIT", projectScope = scope, organization = organization,
            siteurl = "http://localhost:9000/$name", createdDate = Date()
        )
        try {
            repositories.importRepository(project, requireNotNull(form.url), form.authId, form.authPw)
            projects.save(project)
            projectUsers.save(ProjectUser(user = users.getReferenceById(SITE_MANAGER_ID), project = project, role = roles.getReferenceById(RoleType.SITEMANAGER.id)))
            menuSettings.save(
                ProjectMenuSetting(
                    project = project, code = "code" in form.menus, issue = "issue" in form.menus, pullRequest = "pullRequest" in form.menus,
                    review = "review" in form.menus, milestone = "milestone" in form.menus, board = "board" in form.menus, wiki = "wiki" in form.menus
                )
            )
            if (ownerIsUser) projectUsers.save(ProjectUser(user = creator, project = project, role = roles.getReferenceById(RoleType.MANAGER.id)))
            audit.record(dev.yona.domain.audit.auditProject("project.created", project, mapOf("via" to "import", "source" to dev.yona.domain.audit.AuditDetails.safeUrl(form.url), "scope" to project.projectScope?.name)))
            return ImportResult.Created(project)
        } catch (e: InvalidRemoteException) {
            errors.reject("url", "project.import.error.wrong.url")
        } catch (e: JGitInternalException) {
            errors.reject("url", "project.import.error.wrong.url")
        } catch (e: TransportException) {
            transportError(errors, e, form.authId.isNullOrEmpty() && form.authPw.isNullOrEmpty())
        }
        repositories.deleteRepository(project)
        return ImportResult.Rejected(errors)
    }

    /** 전송 오류를 응답 코드에 맞는 안내로 바꾼다 (v1.6 `addDetailedTransportErrorMessage`). */
    private fun transportError(errors: FormErrors, e: TransportException, hasNoCredentials: Boolean) {
        val message = e.message.orEmpty()
        if (message.contains(JGitText.get().notAuthorized)) {
            if (hasNoCredentials) {
                errors.reject("repoAuth", "required")
                errors.reject("url", "project.import.error.transport.unauthorized")
            } else {
                errors.reject("authId", "project.import.error.transport.failedToAuth")
            }
        } else if (message.contains(MessageFormat.format(JGitText.get().serviceNotPermitted, ""))) {
            errors.reject("url", "project.import.error.transport.forbidden")
        } else {
            // 메시지는 `주소: 응답 코드 응답 메시지` 형식이다.
            val parts = message.split(" ")
            errors.reject("url", messages.getMessage("project.import.error.transport", arrayOf<Any>(parts.getOrElse(1) { message }), org.springframework.context.i18n.LocaleContextHolder.getLocale()))
        }
    }

    private companion object {
        /** 사이트 관리자의 사용자 번호이다 (v1.6 `User.SITE_MANAGER_ID`). */
        const val SITE_MANAGER_ID = 1L
    }
}
