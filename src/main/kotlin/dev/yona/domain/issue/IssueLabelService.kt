package dev.yona.domain.issue

import dev.yona.domain.board.Posting
import dev.yona.domain.project.Project
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 이슈 라벨과 라벨 분류의 저장과 삭제를 담당한다.
 * v1.6 `IssueLabelApp`, `IssueLabel`, `IssueLabelCategory`(`app/controllers/IssueLabelApp.java`, `app/models/IssueLabel*.java`)에 해당한다.
 */
@Service
class IssueLabelService(
    private val labels: IssueLabelRepository,
    private val categories: IssueLabelCategoryRepository,
    private val entityManager: EntityManager
) {

    /** 프로젝트의 라벨을 분류 이름, 라벨 이름 순으로 돌려준다. */
    fun labels(project: Project): List<IssueLabel> = labels.findByProjectIdOrderByCategoryNameAscNameAsc(requireNotNull(project.id))

    /** 프로젝트의 라벨을 번호순으로 돌려준다. v1.6 `project.issueLabels`(정렬 없는 일대다)가 DB 순서 그대로 돌려주는 곳에 쓴다. */
    fun labelsById(project: Project): List<IssueLabel> = labels.findByProjectIdOrderByCategoryNameAscNameAsc(requireNotNull(project.id)).sortedBy { it.id }

    /** 프로젝트의 분류를 이름순으로 돌려준다. */
    fun categories(project: Project): List<IssueLabelCategory> = categories.findByProjectIdOrderByNameAsc(requireNotNull(project.id))

    /** 프로젝트의 분류에서 이름이 같은 라벨을 찾는다. */
    fun findLabel(project: Project, category: IssueLabelCategory, name: String): IssueLabel? =
        labels.findByProjectIdOrderByCategoryNameAscNameAsc(requireNotNull(project.id)).firstOrNull { it.category?.id == category.id && it.name == name }

    /** 프로젝트에서 이름이 같은 분류를 찾는다. */
    fun findCategory(project: Project, name: String): IssueLabelCategory? = categories.findFirstByProjectIdAndName(requireNotNull(project.id), name)

    /**
     * 라벨을 만든다. 분류가 이미 있으면 그대로 쓰고(이때 [exclusive]는 무시한다), 없으면 [exclusive] 설정으로 새로 만든다.
     * 같은 분류에 같은 이름의 라벨이 있으면 만들지 않고 `null`을 돌려준다.
     */
    @Transactional
    fun createLabel(project: Project, name: String, color: String, categoryName: String, exclusive: Boolean): IssueLabel? {
        val category = findCategory(project, categoryName)
            ?: categories.save(IssueLabelCategory(project = project, name = categoryName, isExclusive = exclusive))
        if (labels.existsByProjectIdAndCategoryIdAndName(requireNotNull(project.id), requireNotNull(category.id), name)) return null
        return labels.save(IssueLabel(project = project, category = category, name = name, color = color))
    }

    /** 분류를 만든다. 같은 이름의 분류가 있으면 만들지 않고 `null`을 돌려준다. */
    @Transactional
    fun createCategory(project: Project, name: String, exclusive: Boolean): IssueLabelCategory? {
        if (findCategory(project, name) != null) return null
        return categories.save(IssueLabelCategory(project = project, name = name, isExclusive = exclusive))
    }

    /**
     * 라벨을 지운다. 이슈와 게시글에 붙은 것도 함께 떼고, 그 분류에 남은 라벨이 없으면 분류도 지운다. 라벨이 떨어진 이슈를 돌려준다.
     */
    @Transactional
    fun deleteLabel(label: IssueLabel): List<Issue> {
        val project = requireNotNull(label.project)
        val category = label.category
        val detached = detach(label)
        labels.delete(label)
        labels.flush()
        if (category != null && labels.countByProjectIdAndCategoryId(requireNotNull(project.id), requireNotNull(category.id)) == 0L) {
            categories.delete(category)
        }
        return detached
    }

    /** 분류를 지운다. 속한 라벨은 이슈와 게시글에서 떼고 함께 지운다. */
    @Transactional
    fun deleteCategory(category: IssueLabelCategory) {
        labels.findByCategoryId(requireNotNull(category.id)).forEach {
            detach(it)
            labels.delete(it)
        }
        labels.flush()
        categories.delete(category)
    }

    /**
     * [from] 프로젝트의 라벨을 [to] 프로젝트에 더한다. 분류는 이름으로 찾아 쓰고 없으면 같은 설정으로 만들며,
     * 같은 분류, 이름의 라벨이 이미 있으면 건너뛴다(색은 바꾸지 않는다).
     */
    @Transactional
    fun copyLabels(from: Project, to: Project) {
        for (source in labels(from)) {
            val sourceCategory = requireNotNull(source.category)
            val category = findCategory(to, requireNotNull(sourceCategory.name))
                ?: categories.save(IssueLabelCategory(project = to, name = sourceCategory.name, isExclusive = sourceCategory.isExclusive))
            if (!labels.existsByProjectIdAndCategoryIdAndName(requireNotNull(to.id), requireNotNull(category.id), requireNotNull(source.name))) {
                labels.save(IssueLabel(project = to, category = category, name = source.name, color = source.color))
            }
        }
    }

    /** 라벨을 이슈와 게시글에서 떼고, 라벨이 달려 있던 이슈를 돌려준다. */
    private fun detach(label: IssueLabel): List<Issue> {
        val detached = entityManager.createQuery("select i from Issue i join i.labels l where l.id = :id", Issue::class.java)
            .setParameter("id", label.id).resultList.onEach { it.labels.remove(label) }
        entityManager.createQuery("select p from Posting p join p.labels l where l.id = :id", Posting::class.java)
            .setParameter("id", label.id).resultList.forEach { it.labels.remove(label) }
        entityManager.flush()
        return detached
    }
}
