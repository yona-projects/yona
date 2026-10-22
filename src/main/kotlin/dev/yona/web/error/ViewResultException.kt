package dev.yona.web.error

import org.springframework.web.servlet.ModelAndView

/**
 * 검사 단계(인터셉터)에서 요청을 끝내며 보여 줄 화면을 전달하는 예외이다. [GlobalErrorHandler]가 받아 [view]를 그린다.
 * v1.6의 액션이 컨트롤러를 부르지 않고 `Result`를 곧바로 돌려주던 흐름에 해당한다.
 * 스택을 채우지 않는다(흐름 제어용이다).
 */
class ViewResultException(val view: ModelAndView) : RuntimeException(null, null, false, false)
