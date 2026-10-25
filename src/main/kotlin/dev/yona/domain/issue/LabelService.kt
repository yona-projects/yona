package dev.yona.domain.issue

import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 전역 라벨(언어, 라이선스, OS 등)을 찾고 프로젝트에 붙이고 떼는 서비스이다.
 *
 * 원본: `app/controllers/LabelApp.java`, `app/models/Label.java`, `app/models/Project.java`의 `attachLabel`, `detachLabel`.
 */
@Service
class LabelService(
    private val labels: LabelRepository,
    private val projects: ProjectRepository
) {

    /** 목록 조회 결과이다. [total]이 [items]보다 크면 한도에 걸려 잘린 것이다. */
    data class Page<T>(val items: List<T>, val total: Long)

    /**
     * 분류와 이름에 각각 [category], [query]가 대소문자 없이 들어 있는 라벨의 이름을 최대 [limit]개 돌려준다.
     * [limit]이 0 이하이면 제한하지 않는다(Ebean `setMaxRows(0)`과 같다).
     */
    @Transactional(readOnly = true)
    fun searchNames(category: String, query: String, limit: Int): Page<String> {
        val total = labels.countMatching(category, query)
        val rows = labels.findMatching(category, query, PageRequest.of(0, pageSize(limit)))
        return Page(rows.map { it.name.orEmpty() }, total)
    }

    /** 분류에 [query]가 대소문자 없이 들어 있는 겹치지 않는 분류를 최대 [limit]개 돌려준다. */
    @Transactional(readOnly = true)
    fun searchCategories(query: String, limit: Int): Page<String> =
        Page(labels.findCategories(query, PageRequest.of(0, pageSize(limit))), labels.countCategories(query))

    private fun pageSize(limit: Int) = if (limit > 0) limit else Int.MAX_VALUE

    /**
     * [label]을 [project]에 붙인다. 이미 붙어 있으면 `false`이다 (v1.6 `Project.attachLabel`).
     */
    @Transactional
    fun attach(project: Project, label: Label): Boolean {
        val managed = projects.findById(requireNotNull(project.id)).get()
        if (managed.labels.any { it.id == label.id }) return false
        managed.labels.add(label)
        projects.save(managed)
        return true
    }

    /**
     * [label]을 [project]에서 떼고, 쓰는 프로젝트가 더는 없으면 라벨도 지운다 (v1.6 `Project.detachLabel`).
     */
    @Transactional
    fun detach(project: Project, label: Label) {
        val managed = projects.findById(requireNotNull(project.id)).get()
        managed.labels.removeIf { it.id == label.id }
        projects.saveAndFlush(managed)
        if (projects.countByLabelsId(requireNotNull(label.id)) == 0L) labels.delete(label)
    }

    /** 분류와 이름이 같은 라벨을 찾고 없으면 만든다. 만들었으면 `second`가 `true`이다 (v1.6 `ProjectApp.attachLabel`). */
    @Transactional
    fun findOrCreate(category: String?, name: String): Pair<Label, Boolean> {
        labels.findFirstByCategoryAndName(category, name)?.let { return it to false }
        return labels.saveAndFlush(Label(category = category ?: DEFAULT_CATEGORY, name = name)) to true
    }

    companion object {
        /** 분류 없이 만든 라벨의 분류이다 (v1.6 `new Label(null, name)`). */
        const val DEFAULT_CATEGORY = "Label"
    }
}
