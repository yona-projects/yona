package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project

interface IssueLabelService {
    fun getLabels(projectId: Long): List<IssueLabel>
    fun getCategories(projectId: Long): List<IssueLabelCategory>
    fun createLabel(projectId: Long, categoryId: Long, name: String, color: String): IssueLabel
    fun createCategory(projectId: Long, name: String, isExclusive: Boolean): IssueLabelCategory
    // yona IssueLabelApp.newLabel() 대응 — categoryName으로 카테고리를 찾거나(없으면 새로 만들어) 라벨을
    // 추가한다. 같은 카테고리+이름의 라벨이 이미 있으면 null을 반환한다(legacy noContent() 대응).
    fun newLabelByCategoryName(projectId: Long, categoryName: String, categoryIsExclusive: Boolean, labelName: String, labelColor: String): IssueLabel?
    fun updateLabel(labelId: Long, name: String, color: String, categoryId: Long): IssueLabel
    fun updateCategory(categoryId: Long, name: String, isExclusive: Boolean): IssueLabelCategory
    fun copyLabels(fromProjectId: Long, toProjectId: Long): List<IssueLabel>
    // yona IssueApp.transferLabels()(IssueLabel.copyIssueLabel()/findExistLabel()) 대응.
    // 이슈를 다른 프로젝트로 옮길 때 그 이슈의 라벨들을 대상 프로젝트로 이전한다. copyLabels()와 달리
    // 이미 대상 프로젝트에 존재하는 라벨도 반환 결과에 포함한다(옮겨진 이슈의 최종 라벨 집합이므로).
    fun transferLabelsForIssue(labels: Set<IssueLabel>, toProject: Project): Set<IssueLabel>
    fun deleteLabel(labelId: Long)
    fun deleteCategory(categoryId: Long)
}

// yona IssueLabelApp.updateCategory()의 동일 프로젝트 내 이름 중복 검사 대응.
class DuplicateLabelCategoryNameException(message: String) : RuntimeException(message)
