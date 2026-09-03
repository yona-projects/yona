package com.github.yonaprojects.yona.domain.project

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

// yona models/TitleHead.java 대응 (P1-103) 서비스 로직 단위 테스트.
class TitleHeadServiceImplSpec : DescribeSpec({
    val titleHeadRepository = mockk<TitleHeadRepository>()
    val service = TitleHeadServiceImpl(titleHeadRepository)
    val project = Project(id = 1L, name = "TestProject", owner = "owner")

    beforeTest {
        clearMocks(titleHeadRepository)
    }

    describe("saveTitleHeadKeyword()") {
        it("대괄호로 시작하는 머리말이 없으면 저장을 시도하지 않아야 한다") {
            service.saveTitleHeadKeyword(project, "그냥 제목입니다")

            verify(exactly = 0) { titleHeadRepository.findByProjectIdAndHeadKeyword(any(), any()) }
        }

        it("대괄호 안이 비어있어(길이 2) isSurroundedByBracket 조건을 만족하지 않으면 저장하지 않아야 한다") {
            service.saveTitleHeadKeyword(project, "[] 나머지")

            verify(exactly = 0) { titleHeadRepository.findByProjectIdAndHeadKeyword(any(), any()) }
        }

        it("기존에 같은 키워드가 없으면 frequency 1로 새로 저장해야 한다") {
            every { titleHeadRepository.findByProjectIdAndHeadKeyword(1L, "Bug") } returns null
            val saved = slot<TitleHead>()
            every { titleHeadRepository.save(capture(saved)) } returns mockk()

            service.saveTitleHeadKeyword(project, "[Bug] 뭔가 고침")

            saved.captured.headKeyword shouldBe "Bug"
            saved.captured.frequency shouldBe 1
        }

        it("기존에 같은 키워드가 있으면 frequency를 증가시켜 저장해야 한다") {
            val existing = TitleHead(id = 10L, project = project, headKeyword = "Bug", frequency = 3)
            every { titleHeadRepository.findByProjectIdAndHeadKeyword(1L, "Bug") } returns existing
            every { titleHeadRepository.save(existing) } returns existing

            service.saveTitleHeadKeyword(project, "[Bug] 뭔가 고침")

            existing.frequency shouldBe 4
            verify(exactly = 1) { titleHeadRepository.save(existing) }
        }

        it("여러 개의 대괄호 머리말이 연달아 있으면 모두 각각 처리해야 한다") {
            every { titleHeadRepository.findByProjectIdAndHeadKeyword(1L, "Bug") } returns null
            every { titleHeadRepository.findByProjectIdAndHeadKeyword(1L, "UI") } returns null
            every { titleHeadRepository.save(any()) } returns mockk()

            service.saveTitleHeadKeyword(project, "[Bug][UI] 제목")

            verify(exactly = 1) { titleHeadRepository.findByProjectIdAndHeadKeyword(1L, "Bug") }
            verify(exactly = 1) { titleHeadRepository.findByProjectIdAndHeadKeyword(1L, "UI") }
        }
    }

    describe("deleteTitleHeadKeyword()") {
        it("기존에 같은 키워드가 없으면(null) 아무 것도 하지 않아야 한다") {
            every { titleHeadRepository.findByProjectIdAndHeadKeyword(1L, "Bug") } returns null

            service.deleteTitleHeadKeyword(project, "[Bug] 뭔가 고침")

            verify(exactly = 0) { titleHeadRepository.save(any()) }
            verify(exactly = 0) { titleHeadRepository.delete(any()) }
        }

        it("frequency가 1에서 0이 되면 삭제해야 한다") {
            val existing = TitleHead(id = 10L, project = project, headKeyword = "Bug", frequency = 1)
            every { titleHeadRepository.findByProjectIdAndHeadKeyword(1L, "Bug") } returns existing
            every { titleHeadRepository.delete(existing) } returns Unit

            service.deleteTitleHeadKeyword(project, "[Bug] 뭔가 고침")

            existing.frequency shouldBe 0
            verify(exactly = 1) { titleHeadRepository.delete(existing) }
            verify(exactly = 0) { titleHeadRepository.save(any()) }
        }

        it("frequency가 0이 아니게 감소하면 저장만 하고 삭제하지 않아야 한다") {
            val existing = TitleHead(id = 10L, project = project, headKeyword = "Bug", frequency = 3)
            every { titleHeadRepository.findByProjectIdAndHeadKeyword(1L, "Bug") } returns existing
            every { titleHeadRepository.save(existing) } returns existing

            service.deleteTitleHeadKeyword(project, "[Bug] 뭔가 고침")

            existing.frequency shouldBe 2
            verify(exactly = 1) { titleHeadRepository.save(existing) }
            verify(exactly = 0) { titleHeadRepository.delete(any()) }
        }
    }

    describe("search()") {
        it("프로젝트 id와 검색어로 리포지토리를 조회해 그대로 반환해야 한다") {
            val results = listOf(TitleHead(id = 1L, project = project, headKeyword = "Bug", frequency = 5))
            every { titleHeadRepository.findByProjectIdAndHeadKeywordContainingIgnoreCase(1L, "bu") } returns results

            val result = service.search(project, "bu")

            result shouldBe results
        }
    }
})
