package dev.yona.web

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.misc.AttachmentService
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertEquals

/** 읽을 수 없는 첨부가 섞여 있어도 목록 전체가 사라지지 않고 그 첨부만 표시되는지 검증한다(R-58-6). */
@Transactional
class AttachmentListingUnreadableTest : AbstractIntegrationTest() {

    @Autowired private lateinit var listing: AttachmentListing
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var attachments: AttachmentService

    @Test
    fun `읽을 수 없는 첨부는 이름 없이 표시만 하고 목록은 유지한다 (R-58-6)`() {
        val project: Project = projects.save(Project(owner = "unr${System.nanoTime()}", name = "p", vcs = "GIT", projectScope = ProjectScope.PRIVATE))
        val file = File.createTempFile("unr", ".png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        attachments.store(file, "secret.png", ResourceType.PROJECT, project.id!!, "x")
        // 비로그인은 비공개 프로젝트의 첨부를 읽을 수 없다. 기존 방식은 목록 전체가 null이다
        assertEquals(null, listing.fileList(ResourceType.PROJECT, project.id!!))
        assertEquals(listOf(mapOf("unreadable" to "true")), listing.fileListMarkingUnreadable(ResourceType.PROJECT, project.id!!))
    }
}
