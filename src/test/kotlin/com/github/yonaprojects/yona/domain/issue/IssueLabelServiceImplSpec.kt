package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional

class IssueLabelServiceImplSpec : DescribeSpec({
    val issueLabelRepository = mockk<IssueLabelRepository>()
    val issueLabelCategoryRepository = mockk<IssueLabelCategoryRepository>()
    val projectRepository = mockk<ProjectRepository>()

    val service = IssueLabelServiceImpl(issueLabelRepository, issueLabelCategoryRepository, projectRepository)

    val project = Project(id = 1L, name = "TestProject", owner = "gildong")
    val oldCategory = IssueLabelCategory(id = 10L, name = "기존 카테고리", isExclusive = false, project = project)
    val newCategory = IssueLabelCategory(id = 11L, name = "새 카테고리", isExclusive = false, project = project)

    beforeTest {
        clearMocks(issueLabelRepository, issueLabelCategoryRepository, projectRepository, answers = false)
    }

    // yona IssueLabel.exists()(project+category+name 복합 유일성) 대응 (P1-54). 같은 프로젝트라도
    // 카테고리가 다르면 같은 이름의 라벨을 허용해야 한다 — project+name 단일 유일성이었던
    // 기존 축약을 legacy와 동일하게 되돌린다.
    describe("IssueLabelServiceImpl.createLabel") {
        it("같은 프로젝트라도 카테고리가 다르면 같은 이름의 라벨을 새로 생성해야 한다") {
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { issueLabelCategoryRepository.findById(11L) } returns Optional.of(newCategory)
            every { issueLabelRepository.findByProjectAndCategoryAndName(project, newCategory, "버그") } returns null

            val captured = slot<IssueLabel>()
            every { issueLabelRepository.save(capture(captured)) } answers { firstArg() }

            val result = service.createLabel(projectId = 1L, categoryId = 11L, name = "버그", color = "#ff0000")

            captured.captured.name shouldBe "버그"
            captured.captured.category shouldBe newCategory
            result.name shouldBe "버그"
        }

        it("같은 프로젝트+카테고리+이름이 이미 있으면 기존 라벨을 재사용해야 한다") {
            val existing = IssueLabel(id = 200L, category = oldCategory, color = "#ff0000", name = "버그", project = project)
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { issueLabelCategoryRepository.findById(10L) } returns Optional.of(oldCategory)
            every { issueLabelRepository.findByProjectAndCategoryAndName(project, oldCategory, "버그") } returns existing

            val result = service.createLabel(projectId = 1L, categoryId = 10L, name = "버그", color = "#000000")

            result shouldBe existing
            verify(exactly = 0) { issueLabelRepository.save(any()) }
        }
    }

    describe("IssueLabelServiceImpl.updateLabel") {
        it("라벨의 이름/색상/카테고리를 변경 후 저장해야 한다") {
            val label = IssueLabel(id = 100L, category = oldCategory, color = "#111111", name = "옛 이름", project = project)
            every { issueLabelRepository.findById(100L) } returns Optional.of(label)
            every { issueLabelCategoryRepository.findById(11L) } returns Optional.of(newCategory)

            val captured = slot<IssueLabel>()
            every { issueLabelRepository.save(capture(captured)) } answers { firstArg() }

            val result = service.updateLabel(labelId = 100L, name = "새 이름", color = "#ffffff", categoryId = 11L)

            captured.captured.name shouldBe "새 이름"
            captured.captured.color shouldBe "#ffffff"
            captured.captured.category shouldBe newCategory
            result.name shouldBe "새 이름"
        }

        it("존재하지 않는 라벨 id면 IllegalArgumentException을 던져야 한다") {
            every { issueLabelRepository.findById(999L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.updateLabel(labelId = 999L, name = "새 이름", color = "#ffffff", categoryId = 11L)
            }

            verify(exactly = 0) { issueLabelRepository.save(any()) }
        }

        it("존재하지 않는 카테고리 id면 IllegalArgumentException을 던져야 한다") {
            val label = IssueLabel(id = 100L, category = oldCategory, color = "#111111", name = "옛 이름", project = project)
            every { issueLabelRepository.findById(100L) } returns Optional.of(label)
            every { issueLabelCategoryRepository.findById(999L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.updateLabel(labelId = 100L, name = "새 이름", color = "#ffffff", categoryId = 999L)
            }

            verify(exactly = 0) { issueLabelRepository.save(any()) }
        }
    }

    describe("IssueLabelServiceImpl.updateCategory") {
        it("카테고리의 이름/exclusive 여부를 변경 후 저장해야 한다") {
            every { issueLabelCategoryRepository.findById(10L) } returns Optional.of(oldCategory)
            every { issueLabelCategoryRepository.findByProjectAndName(project, "변경된 이름") } returns null

            val captured = slot<IssueLabelCategory>()
            every { issueLabelCategoryRepository.save(capture(captured)) } answers { firstArg() }

            val result = service.updateCategory(categoryId = 10L, name = "변경된 이름", isExclusive = true)

            captured.captured.name shouldBe "변경된 이름"
            captured.captured.isExclusive shouldBe true
            result.name shouldBe "변경된 이름"
        }

        it("존재하지 않는 카테고리 id면 IllegalArgumentException을 던져야 한다") {
            every { issueLabelCategoryRepository.findById(999L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.updateCategory(categoryId = 999L, name = "새 이름", isExclusive = false)
            }

            verify(exactly = 0) { issueLabelCategoryRepository.save(any()) }
        }

        it("같은 프로젝트에 동일한 이름의 다른 카테고리가 있으면 DuplicateLabelCategoryNameException을 던져야 한다") {
            val otherCategory = IssueLabelCategory(id = 12L, name = "중복 이름", isExclusive = false, project = project)
            every { issueLabelCategoryRepository.findById(10L) } returns Optional.of(oldCategory)
            every { issueLabelCategoryRepository.findByProjectAndName(project, "중복 이름") } returns otherCategory

            shouldThrow<DuplicateLabelCategoryNameException> {
                service.updateCategory(categoryId = 10L, name = "중복 이름", isExclusive = false)
            }

            verify(exactly = 0) { issueLabelCategoryRepository.save(any()) }
        }

        it("동일 이름 카테고리를 찾았는데 id가 null이면(비영속) 다른 카테고리로 취급해 예외를 던져야 한다") {
            val duplicateWithNullId = IssueLabelCategory(id = null, name = "중복 이름", isExclusive = false, project = project)
            every { issueLabelCategoryRepository.findById(10L) } returns Optional.of(oldCategory)
            every { issueLabelCategoryRepository.findByProjectAndName(project, "중복 이름") } returns duplicateWithNullId

            shouldThrow<DuplicateLabelCategoryNameException> {
                service.updateCategory(categoryId = 10L, name = "중복 이름", isExclusive = false)
            }
        }

        it("자기 자신과 이름이 같으면(변경 없음) 중복으로 취급하지 않아야 한다") {
            val sameNameAsSelf = IssueLabelCategory(id = 10L, name = "기존 카테고리", isExclusive = false, project = project)
            every { issueLabelCategoryRepository.findById(10L) } returns Optional.of(sameNameAsSelf)
            every { issueLabelCategoryRepository.findByProjectAndName(project, "기존 카테고리") } returns sameNameAsSelf
            every { issueLabelCategoryRepository.save(any()) } answers { firstArg() }

            val result = service.updateCategory(categoryId = 10L, name = "기존 카테고리", isExclusive = true)

            result.isExclusive shouldBe true
        }
    }

    describe("IssueLabelServiceImpl.copyLabels") {
        val fromProject = Project(id = 2L, name = "FromProject", owner = "gildong")
        val toProject = Project(id = 3L, name = "ToProject", owner = "gildong")

        it("원본 프로젝트의 라벨/카테고리를 대상 프로젝트에 복사해야 한다(카테고리·라벨 모두 신규)") {
            val fromCategory = IssueLabelCategory(id = 20L, name = "버그", isExclusive = true, project = fromProject)
            val fromLabel = IssueLabel(id = 30L, category = fromCategory, color = "#ff0000", name = "critical", project = fromProject)

            every { projectRepository.findById(2L) } returns Optional.of(fromProject)
            every { projectRepository.findById(3L) } returns Optional.of(toProject)
            every { issueLabelRepository.findByProject(fromProject) } returns listOf(fromLabel)
            every { issueLabelCategoryRepository.findByProjectAndName(toProject, "버그") } returns null

            val savedCategory = slot<IssueLabelCategory>()
            every { issueLabelCategoryRepository.save(capture(savedCategory)) } answers {
                firstArg<IssueLabelCategory>().apply { id = 21L }
            }
            every { issueLabelRepository.findByProjectAndCategoryAndName(toProject, any(), "critical") } returns null

            val savedLabel = slot<IssueLabel>()
            every { issueLabelRepository.save(capture(savedLabel)) } answers { firstArg() }

            val result = service.copyLabels(fromProjectId = 2L, toProjectId = 3L)

            savedCategory.captured.name shouldBe "버그"
            savedCategory.captured.isExclusive shouldBe true
            savedCategory.captured.project shouldBe toProject
            savedLabel.captured.name shouldBe "critical"
            savedLabel.captured.color shouldBe "#ff0000"
            savedLabel.captured.project shouldBe toProject
            result.size shouldBe 1
        }

        it("대상 프로젝트에 같은 이름의 카테고리가 이미 있으면 재사용하고 새로 만들지 않아야 한다") {
            val fromCategory = IssueLabelCategory(id = 20L, name = "버그", isExclusive = true, project = fromProject)
            val fromLabel = IssueLabel(id = 30L, category = fromCategory, color = "#ff0000", name = "critical", project = fromProject)
            val existingCategory = IssueLabelCategory(id = 99L, name = "버그", isExclusive = true, project = toProject)

            every { projectRepository.findById(2L) } returns Optional.of(fromProject)
            every { projectRepository.findById(3L) } returns Optional.of(toProject)
            every { issueLabelRepository.findByProject(fromProject) } returns listOf(fromLabel)
            every { issueLabelCategoryRepository.findByProjectAndName(toProject, "버그") } returns existingCategory
            every { issueLabelRepository.findByProjectAndCategoryAndName(toProject, existingCategory, "critical") } returns null
            every { issueLabelRepository.save(any()) } answers { firstArg() }

            service.copyLabels(fromProjectId = 2L, toProjectId = 3L)

            verify(exactly = 0) { issueLabelCategoryRepository.save(any()) }
        }

        it("대상 프로젝트에 같은 이름의 라벨이 이미 있으면 건너뛰어야 한다") {
            val fromCategory = IssueLabelCategory(id = 20L, name = "버그", isExclusive = true, project = fromProject)
            val fromLabel = IssueLabel(id = 30L, category = fromCategory, color = "#ff0000", name = "critical", project = fromProject)
            val existingLabel = IssueLabel(id = 88L, category = fromCategory, color = "#000000", name = "critical", project = toProject)

            every { projectRepository.findById(2L) } returns Optional.of(fromProject)
            every { projectRepository.findById(3L) } returns Optional.of(toProject)
            every { issueLabelRepository.findByProject(fromProject) } returns listOf(fromLabel)
            every { issueLabelCategoryRepository.findByProjectAndName(toProject, "버그") } returns null
            every { issueLabelCategoryRepository.save(any()) } answers { firstArg<IssueLabelCategory>().apply { id = 21L } }
            every { issueLabelRepository.findByProjectAndCategoryAndName(toProject, any(), "critical") } returns existingLabel

            val result = service.copyLabels(fromProjectId = 2L, toProjectId = 3L)

            result.size shouldBe 0
            verify(exactly = 0) { issueLabelRepository.save(any()) }
        }

        it("원본 프로젝트가 없으면 IllegalArgumentException을 던져야 한다") {
            every { projectRepository.findById(2L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.copyLabels(fromProjectId = 2L, toProjectId = 3L)
            }
        }
    }

    // yona IssueApp.transferLabels()(IssueLabel.copyIssueLabel()/findExistLabel()) 대응 (P1-48).
    describe("IssueLabelServiceImpl.transferLabelsForIssue") {
        val toProject = Project(id = 3L, name = "ToProject", owner = "gildong")

        it("대상 프로젝트에 같은 카테고리/이름의 라벨이 없으면 새로 만들어 반환해야 한다") {
            val fromCategory = IssueLabelCategory(id = 20L, name = "버그", isExclusive = true, project = project)
            val fromLabel = IssueLabel(id = 30L, category = fromCategory, color = "#ff0000", name = "critical", project = project)

            every { issueLabelCategoryRepository.findByProjectAndName(toProject, "버그") } returns null
            val savedCategory = slot<IssueLabelCategory>()
            every { issueLabelCategoryRepository.save(capture(savedCategory)) } answers {
                firstArg<IssueLabelCategory>().apply { id = 21L }
            }
            every { issueLabelRepository.findByProjectAndCategoryAndName(toProject, any(), "critical") } returns null
            val savedLabel = slot<IssueLabel>()
            every { issueLabelRepository.save(capture(savedLabel)) } answers { firstArg() }

            val result = service.transferLabelsForIssue(setOf(fromLabel), toProject)

            result.size shouldBe 1
            savedLabel.captured.name shouldBe "critical"
            savedLabel.captured.color shouldBe "#ff0000"
            savedLabel.captured.project shouldBe toProject
        }

        it("대상 프로젝트에 같은 카테고리/이름의 라벨이 이미 있으면 재사용해 결과에 포함해야 한다(copyLabels와 달리 건너뛰지 않음)") {
            val fromCategory = IssueLabelCategory(id = 20L, name = "버그", isExclusive = true, project = project)
            val fromLabel = IssueLabel(id = 30L, category = fromCategory, color = "#ff0000", name = "critical", project = project)
            val existingCategory = IssueLabelCategory(id = 99L, name = "버그", isExclusive = true, project = toProject)
            val existingLabel = IssueLabel(id = 88L, category = existingCategory, color = "#000000", name = "critical", project = toProject)

            every { issueLabelCategoryRepository.findByProjectAndName(toProject, "버그") } returns existingCategory
            every { issueLabelRepository.findByProjectAndCategoryAndName(toProject, existingCategory, "critical") } returns existingLabel

            val result = service.transferLabelsForIssue(setOf(fromLabel), toProject)

            result shouldBe setOf(existingLabel)
            verify(exactly = 0) { issueLabelCategoryRepository.save(any()) }
            verify(exactly = 0) { issueLabelRepository.save(any()) }
        }

        it("라벨이 여러 개면 전부 이전한 결과를 반환해야 한다") {
            val fromCategory = IssueLabelCategory(id = 20L, name = "버그", isExclusive = true, project = project)
            val fromLabel1 = IssueLabel(id = 30L, category = fromCategory, color = "#ff0000", name = "critical", project = project)
            val fromLabel2 = IssueLabel(id = 31L, category = fromCategory, color = "#00ff00", name = "minor", project = project)
            val existingCategory = IssueLabelCategory(id = 99L, name = "버그", isExclusive = true, project = toProject)

            every { issueLabelCategoryRepository.findByProjectAndName(toProject, "버그") } returns existingCategory
            every { issueLabelRepository.findByProjectAndCategoryAndName(toProject, existingCategory, "critical") } returns null
            every { issueLabelRepository.findByProjectAndCategoryAndName(toProject, existingCategory, "minor") } returns null
            every { issueLabelRepository.save(any()) } answers { firstArg() }

            val result = service.transferLabelsForIssue(setOf(fromLabel1, fromLabel2), toProject)

            result.map { it.name }.toSet() shouldBe setOf("critical", "minor")
        }

        it("라벨이 없으면 빈 집합을 반환해야 한다") {
            val result = service.transferLabelsForIssue(emptySet(), toProject)

            result shouldBe emptySet()
            verify(exactly = 0) { issueLabelCategoryRepository.save(any()) }
        }
    }
})
