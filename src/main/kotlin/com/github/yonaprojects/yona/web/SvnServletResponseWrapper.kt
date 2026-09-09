package com.github.yonaprojects.yona.web

import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper

// P3-34 실사용 검증(SvnHttpProtocolIntegrationSpec) 중 발견한 버그의 수정.
//
// 근본원인: SVNKit의 DAVServlet은 WebDAV/DeltaV 프로토콜의 정상적인 협상 과정에서도(예: 커밋 도중
// 아직 서버에 없는 파일을 추가하기 전에 클라이언트가 먼저 보내는 HEAD 확인 요청) response.sendError
// (404)를 호출한다 — svn 클라이언트 입장에선 "아직 없으니 추가하라"는 정상 신호일 뿐 애플리케이션
// 오류가 아니다. 그런데 Spring Boot는 서블릿의 sendError() 호출을 감지하면 호출 주체가 무엇이든
// 무조건 컨테이너 표준 에러 페이지 메커니즘("/error"로 재-dispatch)을 가로챈다. 이 재-dispatch
// 시점에 DAVServlet이 이미 그 응답에 설정해둔 Content-Type: text/xml이 그대로 남아있는 채로
// BasicErrorController가 이어받는데, Spring MVC의 컨텐츠 협상은 이 Content-Type을 그대로 존중해
// 에러 바디(LinkedHashMap)를 text/xml로 직렬화하려 시도한다 — 이 앱에는 XML 메시지 컨버터가
// 등록돼 있지 않아 HttpMessageNotWritableException을 던지고, 결과적으로 정상적인 404 프로토콜
// 신호가 빈 바디의 500으로 뒤바뀌어 실제 svn commit이 깨진다(SvnHttpProtocolIntegrationSpec으로
// 재현/고정).
//
// DAVServlet에 넘기는 응답에 한해 sendError()가 컨테이너의 전역 에러 페이지 재-dispatch를
// 트리거하지 않도록 상태 코드만 직접 설정한다(raw wire 프로토콜 서블릿을 감싸는 통상적인 패턴 —
// setStatus()는 sendError()와 달리 컨테이너의 선언적 에러 페이지 처리를 유발하지 않는다). 이
// 서블릿은 어차피 자기 완결적인 WebDAV 응답(상태 코드 + 필요하면 자체 XML 바디)을 직접 만들어내는
// raw 서블릿이라, Spring MVC의 HTML/JSON 에러 페이지가 끼어들 이유가 없다.
class SvnServletResponseWrapper(
    response: HttpServletResponse
) : HttpServletResponseWrapper(response) {

    override fun sendError(sc: Int) {
        status = sc
    }

    override fun sendError(sc: Int, msg: String?) {
        status = sc
    }
}
