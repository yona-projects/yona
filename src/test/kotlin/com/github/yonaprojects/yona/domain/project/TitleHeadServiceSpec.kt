package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.AbstractIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeEmpty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

// yona models/TitleHead.java 대응 (P1-103).
@Transactional
class TitleHeadServiceSpec @Autowired constructor(
    private val titleHeadService: TitleHeadService,
    private val titleHeadRepository: TitleHeadRepository,
    private val projectRepository: ProjectRepository
) : AbstractIntegrationTest() {

    init {
        describe("TitleHeadService") {
            beforeEach {
                titleHeadRepository.deleteAll()
                projectRepository.deleteAll()
            }

            it("대괄호로 시작하는 제목이면 머리말 키워드를 빈도 1로 새로 저장해야 한다") {
                val project = projectRepository.save(Project(name = "proj1", owner = "owner1", projectScope = ProjectScope.PUBLIC))

                titleHeadService.saveTitleHeadKeyword(project, "[Bug] 로그인 오류")

                val found = titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "Bug")
                found shouldNotBe null
                found!!.frequency shouldBe 1
            }

            it("같은 머리말이 이미 있으면 빈도만 1 증가시켜야 한다") {
                val project = projectRepository.save(Project(name = "proj2", owner = "owner1", projectScope = ProjectScope.PUBLIC))

                titleHeadService.saveTitleHeadKeyword(project, "[Bug] 첫번째")
                titleHeadService.saveTitleHeadKeyword(project, "[Bug] 두번째")

                val found = titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "Bug")
                found!!.frequency shouldBe 2
            }

            it("연속된 여러 대괄호 머리말을 모두 각각 저장해야 한다") {
                val project = projectRepository.save(Project(name = "proj3", owner = "owner1", projectScope = ProjectScope.PUBLIC))

                titleHeadService.saveTitleHeadKeyword(project, "[Bug][UI] 화면 깨짐")

                titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "Bug") shouldNotBe null
                titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "UI") shouldNotBe null
            }

            it("대괄호로 시작하지 않는 제목은 머리말을 저장하지 않아야 한다") {
                val project = projectRepository.save(Project(name = "proj4", owner = "owner1", projectScope = ProjectScope.PUBLIC))

                titleHeadService.saveTitleHeadKeyword(project, "그냥 평범한 제목")

                titleHeadRepository.findByProjectIdAndHeadKeywordContainingIgnoreCase(project.id!!, "").shouldBeEmpty()
            }

            it("삭제 시 빈도를 1 감소시키고, 0이 되면 행 자체를 삭제해야 한다") {
                val project = projectRepository.save(Project(name = "proj5", owner = "owner1", projectScope = ProjectScope.PUBLIC))

                titleHeadService.saveTitleHeadKeyword(project, "[Bug] 하나")
                titleHeadService.saveTitleHeadKeyword(project, "[Bug] 둘")
                titleHeadService.deleteTitleHeadKeyword(project, "[Bug] 하나")

                val afterOneDelete = titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "Bug")
                afterOneDelete!!.frequency shouldBe 1

                titleHeadService.deleteTitleHeadKeyword(project, "[Bug] 둘")

                titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, "Bug") shouldBe null
            }

            it("query로 부분일치 검색이 가능해야 한다") {
                val project = projectRepository.save(Project(name = "proj6", owner = "owner1", projectScope = ProjectScope.PUBLIC))

                titleHeadService.saveTitleHeadKeyword(project, "[Bug] 하나")
                titleHeadService.saveTitleHeadKeyword(project, "[Feature] 둘")

                val result = titleHeadService.search(project, "bu")
                result.map { it.headKeyword } shouldContainExactlyInAnyOrder listOf("Bug")
            }

            it("query가 빈 문자열이면 프로젝트의 모든 머리말을 반환해야 한다") {
                val project = projectRepository.save(Project(name = "proj7", owner = "owner1", projectScope = ProjectScope.PUBLIC))

                titleHeadService.saveTitleHeadKeyword(project, "[Bug] 하나")
                titleHeadService.saveTitleHeadKeyword(project, "[Feature] 둘")

                val result = titleHeadService.search(project, "")
                result.map { it.headKeyword } shouldContainExactlyInAnyOrder listOf("Bug", "Feature")
            }
        }
    }
}
