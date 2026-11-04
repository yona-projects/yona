package dev.yona.web.user

import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.misc.ResourceUrls
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import java.util.Date

/**
 * 내 파일 목록이다. v1.6 `UserApp.userFiles`(`app/controllers/UserApp.java:709-722`)와
 * `app/views/user/userFiles.scala.html`에 해당한다.
 *
 * 현재 사용자가 올린 첨부를 최근 순으로 한 쪽에 50개씩 보여 준다. 익명 사용자는 올린 파일이 없으므로 빈 목록이 보인다.
 */
@Controller
@AnonymousCheck
class UserFilesController(
    private val attachments: AttachmentRepository,
    private val resourceUrls: ResourceUrls,
    private val currentUserProvider: CurrentUserProvider
) {

    /**
     * 목록 한 줄이다.
     *
     * @property location 첨부가 붙은 대상의 화면 주소(컨텍스트 경로 제외). 알 수 없으면 `null`이다
     */
    data class FileRow(val id: Long, val name: String, val mimeType: String, val size: Long, val createdDate: Date?, val location: String?) {
        /** 이미지 첨부는 목록에서 미리보기를 보여 준다. */
        val image: Boolean get() = mimeType.startsWith("image/")
    }

    @GetMapping("/user/files")
    @Transactional(readOnly = true)
    fun files(@RequestParam(required = false) pageNum: String?, @RequestParam(required = false) filter: String?): ModelAndView {
        // v1.6은 숫자가 아니거나 0 이하이면 500이었다(R-48-6). 읽을 수 없는 값은 첫 쪽으로 본다
        val number = pageNum?.toIntOrNull()?.takeIf { it >= 1 } ?: 1
        val loginId = currentUserProvider.get().user?.loginId ?: ANONYMOUS_LOGIN_ID
        val page = attachments.findByUser(loginId, filter.orEmpty(), PageRequest.of(number - 1, PAGE_SIZE))
        val rows = page.content.map { rowOf(it) }
        return ModelAndView("user/files")
            .addObject("title", "User Files")
            .addObject("rows", rows)
            .addObject("totalPages", page.totalPages)
            .addObject("menuTab", "TAB_MY_FILES")
    }

    private fun rowOf(attachment: Attachment) = FileRow(
        id = requireNotNull(attachment.id), name = attachment.name.orEmpty(), mimeType = attachment.mimeType.orEmpty(),
        size = attachment.size ?: 0, createdDate = attachment.createdDate,
        location = attachment.containerType?.let { resourceUrls.urlOf(it, attachment.containerId.toString()) }
    )

    private companion object {
        /** 한 쪽에 보이는 파일 수이다 (v1.6 `USER_FILES_COUNT_PER_PAGE`). */
        const val PAGE_SIZE = 50

        /** 익명 사용자의 로그인 ID이다 (v1.6 `User.anonymous.loginId`). */
        const val ANONYMOUS_LOGIN_ID = "anonymous"
    }
}
