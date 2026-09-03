package com.github.yonaprojects.yona.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper

class SvnServletRequestWrapper(
    request: HttpServletRequest,
    private val ownerName: String
) : HttpServletRequestWrapper(request) {

    override fun getServletPath(): String {
        val baseServletPath = super.getServletPath()
        // Spring MVC의 URL 매핑에 관계없이 DAVServlet이 올바른 저장소 경로를 찾을 수 있도록 서블릿 경로를 제공합니다.
        return "/svn/$ownerName"
    }

    override fun getPathInfo(): String? {
        val uri = requestURI
        val context = contextPath
        val prefix = "$context/svn/$ownerName"
        if (uri.startsWith(prefix)) {
            val pathInfo = uri.substring(prefix.length)
            return if (pathInfo.startsWith("/")) pathInfo else "/$pathInfo"
        }
        return super.getPathInfo()
    }
}
