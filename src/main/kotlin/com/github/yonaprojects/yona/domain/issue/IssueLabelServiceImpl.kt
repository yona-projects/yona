package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class IssueLabelServiceImpl(
    private val issueLabelRepository: IssueLabelRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val projectRepository: ProjectRepository
) : IssueLabelService {

    @Transactional(readOnly = true)
    override fun getLabels(projectId: Long): List<IssueLabel> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found: $projectId") }
        return issueLabelRepository.findByProject(project)
    }

    @Transactional(readOnly = true)
    override fun getCategories(projectId: Long): List<IssueLabelCategory> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found: $projectId") }
        return issueLabelCategoryRepository.findByProject(project)
    }

    override fun createLabel(projectId: Long, categoryId: Long, name: String, color: String): IssueLabel {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found: $projectId") }
        val category = issueLabelCategoryRepository.findById(categoryId)
            .orElseThrow { IllegalArgumentException("Category not found: $categoryId") }
        
        // yona IssueLabel.exists()(project+category+name 복합 유일성) 대응 (P1-54).
        val existing = issueLabelRepository.findByProjectAndCategoryAndName(project, category, name)
        if (existing != null) {
            return existing
        }

        val label = IssueLabel(
            category = category,
            color = color,
            name = name,
            project = project
        )
        return issueLabelRepository.save(label)
    }

    override fun createCategory(projectId: Long, name: String, isExclusive: Boolean): IssueLabelCategory {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found: $projectId") }
        
        val existing = issueLabelCategoryRepository.findByProjectAndName(project, name)
        if (existing != null) {
            return existing
        }

        val category = IssueLabelCategory(
            name = name,
            isExclusive = isExclusive,
            project = project
        )
        return issueLabelCategoryRepository.save(category)
    }


    override fun newLabelByCategoryName(projectId: Long, categoryName: String, categoryIsExclusive: Boolean, labelName: String, labelColor: String): IssueLabel? {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found: $projectId") }
        val category = createCategory(projectId, categoryName, categoryIsExclusive)

        // yona IssueLabel.exists()(project+category+name 복합 유일성, P1-54) 대응 — 이미 있으면 null.
        if (issueLabelRepository.findByProjectAndCategoryAndName(project, category, labelName) != null) {
            return null
        }

        val label = IssueLabel(
            category = category,
            color = labelColor,
            name = labelName,
            project = project
        )
        return issueLabelRepository.save(label)
    }

    // yona IssueLabelApp.update() 대응. newLabel()과 달리 이름/색상 중복 검사는 하지 않고 그대로 덮어쓴다.
    override fun updateLabel(labelId: Long, name: String, color: String, categoryId: Long): IssueLabel {
        val label = issueLabelRepository.findById(labelId)
            .orElseThrow { IllegalArgumentException("Label not found: $labelId") }
        val category = issueLabelCategoryRepository.findById(categoryId)
            .orElseThrow { IllegalArgumentException("Category not found: $categoryId") }

        label.name = name
        label.color = color
        label.category = category

        return issueLabelRepository.save(label)
    }

    // yona IssueLabelApp.updateCategory() 대응. 같은 프로젝트 내 다른 카테고리와 이름이 겹치면 거부한다.
    override fun updateCategory(categoryId: Long, name: String, isExclusive: Boolean): IssueLabelCategory {
        val category = issueLabelCategoryRepository.findById(categoryId)
            .orElseThrow { IllegalArgumentException("Category not found: $categoryId") }

        val duplicate = issueLabelCategoryRepository.findByProjectAndName(category.project, name)
        if (duplicate != null && duplicate.id != categoryId) {
            throw DuplicateLabelCategoryNameException("이미 존재하는 카테고리 이름입니다: $name")
        }

        category.name = name
        category.isExclusive = isExclusive

        return issueLabelCategoryRepository.save(category)
    }

    // yona IssueLabel.copyIssueLabels()/copyIssueLabel()/copyIssueLabelCategory() 대응.
    // 원본 프로젝트의 라벨을 대상 프로젝트로 복사하되, 같은 이름의 카테고리/라벨이 이미 있으면
    // 재사용(재생성하지 않음)한다. yona copyIssueLabelCategory()가 카테고리를 먼저 찾거나 만든 뒤
    // copyIssueLabel().exists()(project+category+name 복합 유일성, P1-54)로 중복을 판단하는 순서를
    // 그대로 따른다 — 카테고리를 먼저 정해야 그 카테고리 기준으로 중복 여부를 물을 수 있다.
    override fun copyLabels(fromProjectId: Long, toProjectId: Long): List<IssueLabel> {
        val fromProject = projectRepository.findById(fromProjectId)
            .orElseThrow { IllegalArgumentException("Project not found: $fromProjectId") }
        val toProject = projectRepository.findById(toProjectId)
            .orElseThrow { IllegalArgumentException("Project not found: $toProjectId") }

        val copiedLabels = mutableListOf<IssueLabel>()

        for (fromLabel in issueLabelRepository.findByProject(fromProject)) {
            val category = findOrCreateLabelCategory(toProject, fromLabel.category)

            if (issueLabelRepository.findByProjectAndCategoryAndName(toProject, category, fromLabel.name) != null) {
                continue
            }

            copiedLabels.add(findOrCreateLabel(toProject, category, fromLabel))
        }

        return copiedLabels
    }

    // yona IssueLabel.copyIssueLabel()/findExistLabel() 대응 (P1-48). copyLabels()와 달리 이미
    // 존재하는 라벨도 결과에 포함한다 — 옮겨지는 이슈의 최종 라벨 집합을 그대로 돌려줘야 하기 때문.
    override fun transferLabelsForIssue(labels: Set<IssueLabel>, toProject: Project): Set<IssueLabel> {
        val transferred = mutableSetOf<IssueLabel>()
        for (fromLabel in labels) {
            val category = findOrCreateLabelCategory(toProject, fromLabel.category)
            transferred.add(findOrCreateLabel(toProject, category, fromLabel))
        }
        return transferred
    }

    // yona IssueLabel.copyIssueLabelCategory() 대응 — 대상 프로젝트에 같은 이름의 카테고리가 있으면
    // 재사용하고, 없으면 새로 만든다.
    private fun findOrCreateLabelCategory(toProject: Project, fromCategory: IssueLabelCategory): IssueLabelCategory {
        return issueLabelCategoryRepository.findByProjectAndName(toProject, fromCategory.name)
            ?: issueLabelCategoryRepository.save(
                IssueLabelCategory(
                    name = fromCategory.name,
                    isExclusive = fromCategory.isExclusive,
                    project = toProject
                )
            )
    }

    // yona IssueLabel.copyIssueLabel()의 project+category+name 유일성 재사용 부분(P1-54) 대응.
    private fun findOrCreateLabel(toProject: Project, category: IssueLabelCategory, fromLabel: IssueLabel): IssueLabel {
        return issueLabelRepository.findByProjectAndCategoryAndName(toProject, category, fromLabel.name)
            ?: issueLabelRepository.save(
                IssueLabel(
                    category = category,
                    color = fromLabel.color,
                    name = fromLabel.name,
                    project = toProject
                )
            )
    }

    // yona IssueLabelApp.delete() 대응 — 라벨 삭제 후 해당 카테고리에 남은 라벨이 없으면 카테고리도 함께 삭제한다.
    override fun deleteLabel(labelId: Long) {
        val category = issueLabelRepository.findById(labelId).orElse(null)?.category

        issueLabelRepository.deleteIssueMappings(labelId)
        issueLabelRepository.deletePostingMappings(labelId)
        issueLabelRepository.deleteById(labelId)

        if (category != null && issueLabelRepository.findByCategory(category).isEmpty()) {
            issueLabelCategoryRepository.delete(category)
        }
    }

    override fun deleteCategory(categoryId: Long) {
        val category = issueLabelCategoryRepository.findById(categoryId)
            .orElseThrow { IllegalArgumentException("Category not found: $categoryId") }

        val labels = issueLabelRepository.findByProject(category.project)
            .filter { it.category.id == categoryId }

        labels.forEach { label ->
            label.id?.let { deleteLabel(it) }
        }

        // deleteLabel()이 마지막 라벨 삭제 시 빈 카테고리를 이미 지웠을 수 있다(위 위임 로직) — 남아있을
        // 때만 지운다.
        if (issueLabelCategoryRepository.existsById(categoryId)) {
            issueLabelCategoryRepository.delete(category)
        }
    }
}
