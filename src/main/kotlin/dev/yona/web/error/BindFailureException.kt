package dev.yona.web.error

/**
 * 요청 값을 바인더로 바꾸지 못했음을 알린다. v1.6에서 Play가 라우트의 `QueryStringBindable` 같은 바인더 실패를 `Global.onBadRequest`로
 * 처리하는 것에 해당하며, 400 화면을 `Cache-Control: no-cache` 없이 낸다([GlobalErrorHandler]).
 */
class BindFailureException(message: String = "binder failure") : RuntimeException(message)
