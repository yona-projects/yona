package com.github.yonaprojects.yona.domain.milestone

import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional
import org.springframework.data.domain.Sort

class MilestoneServiceImplSpec : DescribeSpec({
    val milestoneRepository = mockk<MilestoneRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val issueRepository = mockk<IssueRepository>()
    val attachmentService = mockk<AttachmentService>(relaxed = true)
    val watchService = mockk<WatchService>(relaxed = true)
    val service = MilestoneServiceImpl(milestoneRepository, projectRepository, issueRepository, attachmentService, watchService)

    // yona models/resource/ResourcePersistAdapter.java의 postDelete()(deleteRelatedWatch/
    // deleteRelatedUnwatch) 대응 (P1-147).
    describe("MilestoneServiceImpl.deleteMilestone") {
        it("마일스톤 삭제 시 첨부파일과 Watch/Unwatch가 함께 정리되어야 한다") {
            val milestone = Milestone(id = 5L, title = "mile", project = Project(id = 1L, name = "proj", owner = "owner"))
            every { milestoneRepository.findById(5L) } returns Optional.of(milestone)
            every { issueRepository.removeMilestoneFromIssues(milestone) } returns Unit
            every { milestoneRepository.delete(milestone) } returns Unit

            service.deleteMilestone(5L)

            verify { attachmentService.deleteAll(ResourceType.MILESTONE, "5") }
            verify { watchService.deleteAll(ResourceType.MILESTONE, "5") }
            verify { milestoneRepository.delete(milestone) }
        }

        it("존재하지 않는 마일스톤이면 예외가 발생해야 한다") {
            every { milestoneRepository.findById(999L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.deleteMilestone(999L)
            }
        }
    }

    describe("MilestoneServiceImpl.getMilestones") {
        val project = Project(id = 1L, name = "proj", owner = "owner")

        it("orderDir=desc, orderBy=completionRate, state=ALL이면 정렬 없이 findByProject를 호출해야 한다") {
            every { projectRepository.findById(1L) } returns Optional.of(project)
            val sortSlot = slot<Sort>()
            every { milestoneRepository.findByProject(project, capture(sortSlot)) } returns listOf()

            service.getMilestones(1L, State.ALL, "completionRate", "desc")

            sortSlot.captured.isSorted shouldBe false
        }

        it("orderDir=asc, orderBy=title, state=OPEN이면 정렬을 적용해 findByProjectAndState를 호출해야 한다") {
            every { projectRepository.findById(1L) } returns Optional.of(project)
            val sortSlot = slot<Sort>()
            every { milestoneRepository.findByProjectAndState(project, State.OPEN, capture(sortSlot)) } returns listOf()

            service.getMilestones(1L, State.OPEN, "title", "asc")

            sortSlot.captured.isSorted shouldBe true
            sortSlot.captured.getOrderFor("title")?.direction shouldBe Sort.Direction.ASC
        }

        it("프로젝트가 없으면 예외가 발생해야 한다") {
            every { projectRepository.findById(404L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.getMilestones(404L, State.ALL, "title", "asc")
            }
        }
    }

    describe("MilestoneServiceImpl.getMilestone") {
        it("존재하면 마일스톤을 반환해야 한다") {
            val milestone = Milestone(id = 7L, title = "mile", project = Project(id = 1L, name = "proj", owner = "owner"))
            every { milestoneRepository.findById(7L) } returns Optional.of(milestone)

            service.getMilestone(7L) shouldBe milestone
        }

        it("존재하지 않으면 null을 반환해야 한다") {
            every { milestoneRepository.findById(999L) } returns Optional.empty()

            service.getMilestone(999L) shouldBe null
        }
    }

    describe("MilestoneServiceImpl.createMilestone") {
        it("프로젝트가 없으면 예외가 발생해야 한다") {
            every { projectRepository.findById(404L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.createMilestone(404L, Milestone(title = "new", project = Project(id = 0L, name = "", owner = "")))
            }
        }

        it("동일 제목의 마일스톤이 이미 있으면 예외가 발생해야 한다") {
            val project = Project(id = 1L, name = "proj", owner = "owner")
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { milestoneRepository.findByProjectAndTitle(project, "dup") } returns Milestone(id = 2L, title = "dup", project = project)

            shouldThrow<IllegalArgumentException> {
                service.createMilestone(1L, Milestone(title = "dup", project = project))
            }
        }

        it("중복이 없으면 프로젝트와 OPEN 상태를 설정해 저장해야 한다") {
            val project = Project(id = 1L, name = "proj", owner = "owner")
            val milestone = Milestone(title = "new", project = project)
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { milestoneRepository.findByProjectAndTitle(project, "new") } returns null
            every { milestoneRepository.save(milestone) } returns milestone

            val result = service.createMilestone(1L, milestone)

            result.project shouldBe project
            result.state shouldBe State.OPEN
        }
    }

    describe("MilestoneServiceImpl.updateMilestone") {
        it("존재하지 않는 마일스톤이면 예외가 발생해야 한다") {
            every { milestoneRepository.findById(999L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.updateMilestone(999L, "title", null, null, State.OPEN)
            }
        }

        it("다른 마일스톤이 동일 제목을 이미 쓰고 있으면 예외가 발생해야 한다") {
            val project = Project(id = 1L, name = "proj", owner = "owner")
            val milestone = Milestone(id = 5L, title = "old", project = project)
            every { milestoneRepository.findById(5L) } returns Optional.of(milestone)
            every { milestoneRepository.findByProjectAndTitle(project, "dup") } returns Milestone(id = 6L, title = "dup", project = project)

            shouldThrow<IllegalArgumentException> {
                service.updateMilestone(5L, "dup", null, null, State.OPEN)
            }
        }

        it("동일 제목이 자기 자신이면 그대로 수정을 허용해야 한다") {
            val project = Project(id = 1L, name = "proj", owner = "owner")
            val milestone = Milestone(id = 5L, title = "same", project = project)
            every { milestoneRepository.findById(5L) } returns Optional.of(milestone)
            every { milestoneRepository.findByProjectAndTitle(project, "same") } returns milestone
            every { milestoneRepository.save(milestone) } returns milestone

            val result = service.updateMilestone(5L, "same", "contents", null, State.CLOSED)

            result.title shouldBe "same"
            result.contents shouldBe "contents"
            result.state shouldBe State.CLOSED
        }

        // existing.id는 Long?(nullable) 타입이라 existing.id != milestoneId 비교에 별도 null-체크
        // 분기가 생긴다. 영속화된 실제 엔티티라면 id가 항상 존재하겠지만 타입 시스템이 이를 강제하지
        // 않으므로(애플리케이션 차원의 불변식일 뿐), mock으로 id=null인 Milestone을 반환시켜 검증한다.
        it("동일 제목의 다른 마일스톤을 찾았는데 id가 null이면(비영속 상태) 그래도 예외가 발생해야 한다") {
            val project = Project(id = 1L, name = "proj", owner = "owner")
            val milestone = Milestone(id = 5L, title = "old", project = project)
            every { milestoneRepository.findById(5L) } returns Optional.of(milestone)
            every { milestoneRepository.findByProjectAndTitle(project, "dup") } returns Milestone(id = null, title = "dup", project = project)

            shouldThrow<IllegalArgumentException> {
                service.updateMilestone(5L, "dup", null, null, State.OPEN)
            }
        }

        it("동일 제목의 다른 마일스톤이 없으면 수정을 허용해야 한다") {
            val project = Project(id = 1L, name = "proj", owner = "owner")
            val milestone = Milestone(id = 5L, title = "old", project = project)
            every { milestoneRepository.findById(5L) } returns Optional.of(milestone)
            every { milestoneRepository.findByProjectAndTitle(project, "new") } returns null
            every { milestoneRepository.save(milestone) } returns milestone

            val result = service.updateMilestone(5L, "new", null, null, State.OPEN)

            result.title shouldBe "new"
        }
    }
})
