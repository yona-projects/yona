package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.mock.web.MockHttpServletRequest

// SvnController가 DAVServlet에 요청을 넘기기 전에 서블릿 경로/pathInfo를 다시 계산해주는 래퍼.
// Spring MVC의 "/svn/{ownerName}/{projectName}/**" 매핑이 실제 서블릿 경로 개념과 다르게 동작해서,
// DAVServlet(SVNParentPath 기반으로 pathInfo를 해석)이 올바른 저장소 경로를 찾으려면 이 재작성이
// 반드시 정확해야 한다 — 잘못되면 엉뚱한 프로젝트를 열거나 404/500이 난다.
class SvnServletRequestWrapperSpec : DescribeSpec({

    describe("getServletPath()") {
        it("실제 요청 URI와 무관하게 항상 /svn/{ownerName}을 반환해야 한다") {
            val request = MockHttpServletRequest("GET", "/svn/gildong/myproject/trunk/a.txt")
            val wrapper = SvnServletRequestWrapper(request, "gildong")

            wrapper.servletPath shouldBe "/svn/gildong"
        }
    }

    describe("getPathInfo()") {
        it("컨텍스트패스가 없을 때 /svn/{ownerName} 접두사를 제거한 나머지를 pathInfo로 반환해야 한다") {
            val request = MockHttpServletRequest("GET", "/svn/gildong/myproject/trunk/a.txt")
            val wrapper = SvnServletRequestWrapper(request, "gildong")

            wrapper.pathInfo shouldBe "/myproject/trunk/a.txt"
        }

        it("프로젝트 루트 요청(추가 경로 없음)이면 pathInfo가 /{projectName}이어야 한다") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/myproject")
            val wrapper = SvnServletRequestWrapper(request, "gildong")

            wrapper.pathInfo shouldBe "/myproject"
        }

        it("컨텍스트패스가 있어도 정확히 접두사를 제거해야 한다") {
            val request = MockHttpServletRequest("GET", "/svn/gildong/myproject/trunk/a.txt")
            request.contextPath = "/yona"
            request.requestURI = "/yona/svn/gildong/myproject/trunk/a.txt"
            val wrapper = SvnServletRequestWrapper(request, "gildong")

            wrapper.pathInfo shouldBe "/myproject/trunk/a.txt"
        }

        it("URI가 이 owner의 svn 접두사로 시작하지 않으면 원래 요청의 pathInfo로 폴백해야 한다") {
            val request = MockHttpServletRequest("GET", "/other/path")
            val wrapper = SvnServletRequestWrapper(request, "gildong")

            wrapper.pathInfo shouldBe request.pathInfo
        }

        // URI가 접두사와 정확히 일치해(뒤에 남는 부분이 없어) substring 결과가 빈 문자열이면
        // "/"로 시작하지 않으므로 앞에 "/"를 붙여야 한다(else 분기).
        it("URI가 접두사와 정확히 일치하면(남는 경로 없음) pathInfo는 슬래시 하나여야 한다") {
            val request = MockHttpServletRequest("GET", "/svn/gildong")
            val wrapper = SvnServletRequestWrapper(request, "gildong")

            wrapper.pathInfo shouldBe "/"
        }

        it("owner 이름이 다른 프로젝트 경로와 섞이지 않고 정확히 해당 owner의 접두사만 제거해야 한다") {
            // gildong 소유 프로젝트의 svn 요청을 cheolsu 래퍼로 잘못 만들면(방어적 확인) 접두사가
            // 안 맞아 폴백 경로로 빠져야 한다 — SvnController가 URL의 owner 세그먼트로 래퍼를 만드는
            // 것이 왜 중요한지 보여주는 회귀 케이스.
            val request = MockHttpServletRequest("GET", "/svn/gildong/myproject/trunk/a.txt")
            val wrapper = SvnServletRequestWrapper(request, "cheolsu")

            wrapper.pathInfo shouldBe request.pathInfo
        }
    }
})
