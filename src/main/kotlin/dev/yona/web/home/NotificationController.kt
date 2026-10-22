package dev.yona.web.home

import dev.yona.domain.notification.NotificationEventRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

/**
 * 홈의 알림 목록 더 보기이다. v1.6 `NotificationApp.notifications`(`app/controllers/NotificationApp.java`)에 해당한다.
 * `from`번째부터 `limit`개를 화면 조각으로 돌려주고, 홈 화면의 More 단추가 이어 붙인다.
 */
@Controller
@AnonymousCheck
class NotificationController(
    private val currentUserProvider: CurrentUserProvider,
    private val events: NotificationEventRepository,
    private val items: NotificationItemAssembler
) {

    @GetMapping("/notification")
    @Transactional(readOnly = true)
    fun notifications(@RequestParam from: Int, @RequestParam limit: Int): ModelAndView {
        val user = currentUserProvider.get().user
        val notifications = user?.let { items.assemble(events.findByReceiver(it, from, limit), it) }.orEmpty()
        return ModelAndView("index/partial-notifications :: list(notifications=${'$'}{notifications}, from=${'$'}{from}, size=${'$'}{size})")
            .addObject("notifications", notifications).addObject("from", from).addObject("size", limit)
    }
}
