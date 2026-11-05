package dev.yona.domain.project

import dev.yona.util.LegacyText
import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 프로젝트 목록과 이름 검색을 담당한다. v1.6 `ProjectApp.createProjectSearchExpressionList`, `getPagingProjects`,
 * `getProjectsToJSON`에 해당한다.
 *
 * ## 검색 조건
 * - 검색어가 있으면 소유자, 이름, 설명 중 하나에 포함되거나(대소문자 무시) 라벨 이름에 포함되는 프로젝트이다.
 * - 보는 사람이 사이트 관리자가 아니고 사이트가 비공개 프로젝트를 목록에 보이지 않게 하면(`displayPrivateRepositories=false`,
 *   기본값) 공개(PUBLIC) 프로젝트만 보인다. 구성원인 비공개 프로젝트도 목록에는 나오지 않는다.
 * - 정렬은 만든 날짜 내림차순이다.
 */
@Service
class ProjectListService(
    private val projects: ProjectRepository,
    private val users: UserRepository,
    private val properties: YonaProperties
) {

    /** 쪽 번호는 1부터이다. 한 쪽에 [PAGE_SIZE]개씩 돌려준다. */
    @Transactional(readOnly = true)
    fun page(query: String?, labelIds: Set<Long>, pageNum: Int, canSeeAll: Boolean): Page<ProjectListItem> {
        val page = projects.findAll(
            specification(query, labelIds, canSeeAll),
            PageRequest.of(pageNum - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdDate"))
        )
        return page.map { toItem(it) }
    }

    /** JSON 응답용 `소유자/이름` 목록이다. 최대 [MAX_FETCH_PROJECTS]개이며, 전체 개수를 함께 돌려준다. */
    @Transactional(readOnly = true)
    fun names(query: String?, canSeeAll: Boolean): Pair<List<String>, Long> {
        val specification = specification(query, emptySet(), canSeeAll)
        val total = projects.count(specification)
        val found = projects.findAll(specification, PageRequest.of(0, MAX_FETCH_PROJECTS)).content
        return found.map { "${it.owner}/${it.name}" } to total
    }

    private fun specification(query: String?, labelIds: Set<Long>, canSeeAll: Boolean): Specification<Project> =
        Specification { root, criteriaQuery, cb ->
            criteriaQuery?.distinct(true)
            val predicates = mutableListOf<Predicate>()

            if ((query != null && LegacyText.isNotBlank(query))) {
                val pattern = dev.yona.util.LikeEscape.contains(query)
                val labelJoin = root.join<Project, dev.yona.domain.issue.Label>("labels", JoinType.LEFT)
                predicates += cb.or(
                    cb.like(cb.lower(root.get("owner")), pattern, '\\'),
                    cb.like(cb.lower(root.get("name")), pattern, '\\'),
                    cb.like(cb.lower(root.get("overview")), pattern, '\\'),
                    cb.like(cb.lower(labelJoin.get("name")), pattern, '\\')
                )
            }
            if (labelIds.isNotEmpty()) {
                predicates += root.join<Project, dev.yona.domain.issue.Label>("labels", JoinType.INNER).get<Long>("id").`in`(labelIds)
            }
            if (!canSeeAll && !properties.displayPrivateRepositories) {
                predicates += cb.equal(root.get<ProjectScope>("projectScope"), ProjectScope.PUBLIC)
            }
            cb.and(*predicates.toTypedArray())
        }

    private fun toItem(project: Project): ProjectListItem {
        val members = if (project.isPublic()) {
            users.findMembersOfProject(requireNotNull(project.id), RoleType.SITEMANAGER.id)
        } else {
            emptyList()
        }
        return ProjectListItem(
            project = project,
            labels = project.labels.map { ProjectListItem.LabelView(it.id, it.category.orEmpty(), it.name.orEmpty()) },
            members = members
        )
    }

    companion object {
        /** 목록의 쪽 크기이다 (v1.6 `PROJECT_COUNT_PER_PAGE`). */
        const val PAGE_SIZE = 10

        /** JSON 목록에 담는 최대 개수이다 (v1.6 `MAX_FETCH_PROJECTS`). */
        const val MAX_FETCH_PROJECTS = 1000
    }
}

/**
 * 목록의 한 항목이다. 템플릿은 트랜잭션 밖에서 그려지므로 지연 로딩이 필요한 값(라벨, 구성원)은 미리 읽어 담는다.
 *
 * @property members 공개 프로젝트의 구성원(이름 순). 공개가 아니면 비어 있다
 */
data class ProjectListItem(val project: Project, val labels: List<LabelView>, val members: List<User>) {

    /** 목록에 보이는 라벨이다. */
    data class LabelView(val id: Long?, val category: String, val name: String)
}
