package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.util.Optional

class IssueLabelServiceImplExtraSpec : DescribeSpec({
    val issueLabelRepository = mockk<IssueLabelRepository>()
    val issueLabelCategoryRepository = mockk<IssueLabelCategoryRepository>()
    val projectRepository = mockk<ProjectRepository>()

    val service = IssueLabelServiceImpl(issueLabelRepository, issueLabelCategoryRepository, projectRepository)

    val project = Project(id = 1L, name = "TestProject", owner = "gildong")

    beforeTest {
        clearMocks(issueLabelRepository, issueLabelCategoryRepository, projectRepository, answers = false)
    }

    describe("IssueLabelServiceImpl 추가 커버리지 테스트") {
        it("getLabels - 프로젝트를 찾지 못하면 예외") {
            every { projectRepository.findById(999L) } returns Optional.empty()
            shouldThrow<IllegalArgumentException> {
                service.getLabels(999L)
            }
        }
        
        it("getCategories - 프로젝트를 찾지 못하면 예외") {
            every { projectRepository.findById(999L) } returns Optional.empty()
            shouldThrow<IllegalArgumentException> {
                service.getCategories(999L)
            }
        }
        
        it("createLabel - 프로젝트를 찾지 못하면 예외") {
            every { projectRepository.findById(999L) } returns Optional.empty()
            shouldThrow<IllegalArgumentException> {
                service.createLabel(999L, 1L, "name", "color")
            }
        }
        
        it("createLabel - 카테고리를 찾지 못하면 예외") {
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { issueLabelCategoryRepository.findById(999L) } returns Optional.empty()
            shouldThrow<IllegalArgumentException> {
                service.createLabel(1L, 999L, "name", "color")
            }
        }
        
        it("createCategory - 이미 존재하는 카테고리인 경우") {
            every { projectRepository.findById(1L) } returns Optional.of(project)
            val existing = IssueLabelCategory(id = 10L, name = "name", isExclusive = true, project = project)
            every { issueLabelCategoryRepository.findByProjectAndName(project, "name") } returns existing
            
            val result = service.createCategory(1L, "name", true)
            result shouldBe existing
        }
        
        it("createCategory - 프로젝트 찾지 못하면 예외") {
            every { projectRepository.findById(999L) } returns Optional.empty()
            shouldThrow<IllegalArgumentException> {
                service.createCategory(999L, "name", true)
            }
        }
        
        it("newLabelByCategoryName - 새 카테고리 + 새 라벨 생성") {
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { issueLabelCategoryRepository.findByProjectAndName(project, "cat") } returns null
            every { issueLabelCategoryRepository.save(any()) } answers { firstArg<IssueLabelCategory>().apply { id = 11L } }
            every { issueLabelRepository.findByProjectAndCategoryAndName(project, any(), "label") } returns null
            every { issueLabelRepository.save(any()) } answers { firstArg() }
            
            val result = service.newLabelByCategoryName(1L, "cat", true, "label", "color")
            result!!.name shouldBe "label"
        }
        
        it("newLabelByCategoryName - 프로젝트를 찾을 수 없으면 예외를 던져야 한다") {
            every { projectRepository.findById(999L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.newLabelByCategoryName(999L, "cat", true, "label", "color")
            }
        }

        it("newLabelByCategoryName - 이미 라벨이 있으면 null 반환") {
            every { projectRepository.findById(1L) } returns Optional.of(project)
            val existingCategory = IssueLabelCategory(id = 11L, name = "cat", project = project)
            every { issueLabelCategoryRepository.findByProjectAndName(project, "cat") } returns existingCategory
            val existingLabel = IssueLabel(id = 22L, category = existingCategory, name = "label", project = project)
            every { issueLabelRepository.findByProjectAndCategoryAndName(project, existingCategory, "label") } returns existingLabel
            
            val result = service.newLabelByCategoryName(1L, "cat", true, "label", "color")
            result shouldBe null
        }
        
        it("deleteLabel - 라벨 삭제 후 남은 라벨이 있으면 카테고리는 삭제하지 않음") {
            val category = IssueLabelCategory(id = 11L, name = "cat", project = project)
            val label = IssueLabel(id = 22L, category = category, name = "label", project = project)
            val otherLabel = IssueLabel(id = 33L, category = category, name = "other", project = project)
            
            every { issueLabelRepository.findById(22L) } returns Optional.of(label)
            every { issueLabelRepository.deleteIssueMappings(22L) } returns Unit
            every { issueLabelRepository.deletePostingMappings(22L) } returns Unit
            every { issueLabelRepository.deleteById(22L) } returns Unit
            
            every { issueLabelRepository.findByCategory(category) } returns listOf(otherLabel) // not empty
            
            service.deleteLabel(22L)
            
            verify(exactly = 0) { issueLabelCategoryRepository.delete(any()) }
        }
        
        it("deleteLabel - 라벨 삭제 후 남은 라벨이 없으면 카테고리도 삭제") {
            val category = IssueLabelCategory(id = 11L, name = "cat", project = project)
            val label = IssueLabel(id = 22L, category = category, name = "label", project = project)
            
            every { issueLabelRepository.findById(22L) } returns Optional.of(label)
            every { issueLabelRepository.deleteIssueMappings(22L) } returns Unit
            every { issueLabelRepository.deletePostingMappings(22L) } returns Unit
            every { issueLabelRepository.deleteById(22L) } returns Unit
            
            every { issueLabelRepository.findByCategory(category) } returns emptyList() // empty
            every { issueLabelCategoryRepository.delete(category) } returns Unit
            
            service.deleteLabel(22L)
            
            verify(exactly = 1) { issueLabelCategoryRepository.delete(category) }
        }
        
        it("deleteCategory - 카테고리 내의 라벨 모두 삭제 후 카테고리 존재하면 삭제") {
            val category = IssueLabelCategory(id = 11L, name = "cat", project = project)
            val label = IssueLabel(id = 22L, category = category, name = "label", project = project)
            
            every { issueLabelCategoryRepository.findById(11L) } returns Optional.of(category)
            every { issueLabelRepository.findByProject(project) } returns listOf(label)
            
            // mock deleteLabel inner calls
            every { issueLabelRepository.findById(22L) } returns Optional.of(label)
            every { issueLabelRepository.deleteIssueMappings(22L) } returns Unit
            every { issueLabelRepository.deletePostingMappings(22L) } returns Unit
            every { issueLabelRepository.deleteById(22L) } returns Unit
            every { issueLabelRepository.findByCategory(category) } returns emptyList()
            every { issueLabelCategoryRepository.delete(category) } returns Unit
            
            every { issueLabelCategoryRepository.existsById(11L) } returns true
            
            service.deleteCategory(11L)
            
            verify(exactly = 1) { issueLabelRepository.deleteById(22L) }
            verify(exactly = 2) { issueLabelCategoryRepository.delete(category) } // once from deleteLabel, once from deleteCategory
        }
        
        it("deleteCategory - 못 찾으면 예외") {
            every { issueLabelCategoryRepository.findById(999L) } returns Optional.empty()
            shouldThrow<IllegalArgumentException> {
                service.deleteCategory(999L)
            }
        }
        
        it("copyLabels - toProject를 못 찾으면 예외") {
            every { projectRepository.findById(2L) } returns Optional.of(project)
            every { projectRepository.findById(3L) } returns Optional.empty()
            shouldThrow<IllegalArgumentException> {
                service.copyLabels(2L, 3L)
            }
        }

        it("getLabels - 프로젝트를 찾으면 해당 프로젝트의 라벨 목록을 반환해야 한다") {
            val category = IssueLabelCategory(id = 10L, name = "cat", project = project)
            val label = IssueLabel(id = 22L, category = category, name = "label", project = project)
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { issueLabelRepository.findByProject(project) } returns listOf(label)

            val result = service.getLabels(1L)

            result shouldBe listOf(label)
        }

        it("getCategories - 프로젝트를 찾으면 해당 프로젝트의 카테고리 목록을 반환해야 한다") {
            val category = IssueLabelCategory(id = 10L, name = "cat", project = project)
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { issueLabelCategoryRepository.findByProject(project) } returns listOf(category)

            val result = service.getCategories(1L)

            result shouldBe listOf(category)
        }

        // findById(labelId).orElse(null)?.category — 라벨 자체가 없으면(Optional.empty) category가
        // null이 되어 안전호출이 전파되고, "카테고리가 비면 삭제" 검사(if (category != null && ...))가
        // 통째로 건너뛰어진다. 기존 테스트는 전부 라벨이 존재하는 경우만 다뤘다.
        it("deleteLabel - 라벨을 찾지 못해도 예외 없이 삭제 호출만 수행하고 카테고리 정리는 건너뛰어야 한다") {
            every { issueLabelRepository.findById(999L) } returns Optional.empty()
            every { issueLabelRepository.deleteIssueMappings(999L) } returns Unit
            every { issueLabelRepository.deletePostingMappings(999L) } returns Unit
            every { issueLabelRepository.deleteById(999L) } returns Unit

            service.deleteLabel(999L)

            verify(exactly = 0) { issueLabelCategoryRepository.delete(any()) }
            verify(exactly = 0) { issueLabelRepository.findByCategory(any()) }
        }

        // label.id?.let { deleteLabel(it) } — id가 null인 라벨은 deleteLabel 호출 자체를 건너뛴다.
        // 또한 .filter { it.category.id == categoryId }로 다른 카테고리의 라벨은 애초에 제외된다.
        it("deleteCategory - id가 null인 라벨과 다른 카테고리의 라벨은 삭제 대상에서 제외해야 한다") {
            val category = IssueLabelCategory(id = 11L, name = "cat", project = project)
            val otherCategory = IssueLabelCategory(id = 12L, name = "other-cat", project = project)
            val categoryWithNullId = IssueLabelCategory(id = null, name = "no-id-cat", project = project)
            val labelWithNullId = IssueLabel(id = null, category = category, name = "no-id-label", project = project)
            val labelInOtherCategory = IssueLabel(id = 55L, category = otherCategory, name = "other-label", project = project)
            val labelInNullIdCategory = IssueLabel(id = 56L, category = categoryWithNullId, name = "null-cat-label", project = project)

            every { issueLabelCategoryRepository.findById(11L) } returns Optional.of(category)
            every { issueLabelRepository.findByProject(project) } returns listOf(labelWithNullId, labelInOtherCategory, labelInNullIdCategory)
            every { issueLabelCategoryRepository.existsById(11L) } returns true
            every { issueLabelCategoryRepository.delete(category) } returns Unit

            service.deleteCategory(11L)

            verify(exactly = 0) { issueLabelRepository.deleteById(any()) }
            verify(exactly = 1) { issueLabelCategoryRepository.delete(category) }
        }

        // deleteLabel()이 마지막 라벨을 지우며 이미 카테고리를 삭제했다면, deleteCategory()의
        // existsById 체크는 false를 반환해 마지막 delete 호출을 건너뛰어야 한다.
        it("deleteCategory - deleteLabel이 이미 카테고리를 지웠으면(existsById=false) 다시 삭제하지 않아야 한다") {
            val category = IssueLabelCategory(id = 11L, name = "cat", project = project)
            val label = IssueLabel(id = 22L, category = category, name = "label", project = project)

            every { issueLabelCategoryRepository.findById(11L) } returns Optional.of(category)
            every { issueLabelRepository.findByProject(project) } returns listOf(label)
            every { issueLabelRepository.findById(22L) } returns Optional.of(label)
            every { issueLabelRepository.deleteIssueMappings(22L) } returns Unit
            every { issueLabelRepository.deletePostingMappings(22L) } returns Unit
            every { issueLabelRepository.deleteById(22L) } returns Unit
            every { issueLabelRepository.findByCategory(category) } returns emptyList()
            every { issueLabelCategoryRepository.delete(category) } returns Unit
            every { issueLabelCategoryRepository.existsById(11L) } returns false

            service.deleteCategory(11L)

            verify(exactly = 1) { issueLabelCategoryRepository.delete(category) }
        }
    }
})
