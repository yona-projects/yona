package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException

// yona error/requestTextEntityTooLarge.scala.html 대응 (P-템플릿 #53). legacy는 Play의 text body
// parser가 parsers.text.maxLength(설정 없으면 Play 기본값)를 넘는 요청 본문을 413으로 거부하면서
// utils.ErrorViews.RequestTextEntityTooLarge를 렌더링했다(conf/application.conf에 명시적
// override가 없으므로 Play 기본값을 그대로 썼다는 뜻). yona(Spring MVC)에서 이에 대응하는 예외는
// 업로드/멀티파트 요청이 spring.servlet.multipart.max-file-size/max-request-size를 넘을 때 던져지는
// MaxUploadSizeExceededException이다 — 이걸 잡아 동일한 안내 화면을 보여주는 전역 핸들러가 이전에
// 전무했다(어떤 @ControllerAdvice/@ExceptionHandler도 이 예외를 처리하지 않아 컨테이너 기본
// 500/에러페이지로 흘러갔음).
//
// GlobalModelAttributeAdvice.kt와 역할이 다르므로(그쪽은 모델 공통 속성 주입, 이쪽은 예외 처리)
// 별도 파일로 분리했다.
@ControllerAdvice
class GlobalExceptionHandler(
    private val userRepository: UserRepository
) {

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceeded(
        exception: MaxUploadSizeExceededException,
        model: Model,
        response: HttpServletResponse
    ): String {
        response.status = HttpStatus.PAYLOAD_TOO_LARGE.value()
        // MaxUploadSizeExceededException.maxUploadSize는 실제로 초과된 설정값(단일 파일 한도 또는
        // 요청 전체 한도, 어느 쪽이 걸렸는지에 따라 다름)을 그대로 담고 있어 별도로
        // spring.servlet.multipart.max-file-size를 다시 읽어올 필요가 없다 — 실제 발생 원인과
        // 항상 정확히 일치한다.
        model.addAttribute("maxUploadSizeBytes", exception.maxUploadSize)

        // yona UserApp.currentUser().isSiteManager 대응(error/tooLargeText.admin 안내 문구는
        // 사이트매니저에게만 보인다) — @ExceptionHandler 처리 경로는 GlobalModelAttributeAdvice의
        // @ModelAttribute currentUser()가 자동으로 채워주지 않으므로(원 요청의 모델 팩토리를
        // 공유하지 않음) 여기서 직접 SecurityContext로부터 다시 조회한다.
        val authentication = SecurityContextHolder.getContext().authentication
        val currentUser = if (authentication != null && authentication.isAuthenticated && authentication.name != "anonymousUser") {
            userRepository.findByLoginId(authentication.name).orElse(null)
        } else {
            null
        }
        model.addAttribute("currentUser", currentUser)

        return "error/413"
    }
}
