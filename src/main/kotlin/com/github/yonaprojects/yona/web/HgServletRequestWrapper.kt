package com.github.yonaprojects.yona.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper

// HgHttpWireServer(hg4j)는 SVNKit의 DAVServlet과 달리
// getServletPath()/getPathInfo()가 아니라 request.getRequestURI() 하나만으로 자신의 라우팅을 결정한다
// ("/"(정확히 루트)면 wire protocol v2 capabilities-discovery, "/api/"로 시작하면 v2 명령 프레임
// 디스패치, 그 외에는 쿼리스트링의 cmd 파라미터만으로 v1 명령을 디스패치 — HgHttpWireServer.service()
// 참고). Spring MVC가 HgController를 "/hg/{owner}/{project}/**"로 매핑해도 request.getRequestURI()는
// 항상 원본 전체 경로("/hg/{owner}/{project}/api/...")를 그대로 반환하므로, hg4j 입장에서 저장소 루트
// 기준 상대경로를 전제하는 두 분기(위 "/" 루트, "/api/" 접두어)가 이 컨트롤러를 거치면 절대 매치되지
// 않는다 — SvnController가 SvnServletRequestWrapper로 DAVServlet에 "/svn/{owner}" 기준 상대경로를
// 만들어주는 것과 정확히 같은 이유로 이 래퍼가 필요하다. v1 명령 디스패치는 path가 아니라 cmd
// 쿼리파라미터만으로 이뤄지므로 이 래퍼가 없어도 동작은 하지만(실제로 v1 clone/push는 이 래퍼 없이도
// 성공한다), v2 업그레이드 협상까지 정확히 대응하려면 필요하다.
class HgServletRequestWrapper(
    request: HttpServletRequest,
    private val ownerName: String,
    private val projectName: String
) : HttpServletRequestWrapper(request) {

    override fun getRequestURI(): String {
        val uri = super.getRequestURI()
        val prefix = "$contextPath/hg/$ownerName/$projectName"
        if (uri.startsWith(prefix)) {
            val rest = uri.substring(prefix.length)
            return if (rest.isEmpty()) "/" else rest
        }
        return uri
    }
}
