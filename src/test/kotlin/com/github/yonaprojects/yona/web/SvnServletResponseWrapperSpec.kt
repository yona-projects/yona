package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.mock.web.MockHttpServletResponse

// P3-34에서 발견한 버그의 회귀 가드 — SvnController가 DAVServlet에 넘기는 응답을 감싸는 래퍼.
// sendError()가 Spring Boot의 전역 에러 페이지 재-dispatch("/error")를 트리거하지 않도록 상태
// 코드만 직접 설정해야 한다(SvnServletResponseWrapper 클래스 주석 참고). MockHttpServletResponse는
// sendError()와 setStatus()를 구분해서 기록하므로, 이 래퍼가 실제로 setStatus() 경로를 타는지
// (sendError()로 위임하지 않는지) 여기서 직접 검증할 수 있다.
class SvnServletResponseWrapperSpec : DescribeSpec({

    describe("sendError(sc)") {
        it("컨테이너의 에러 페이지 재-dispatch를 트리거하지 않고 상태 코드만 설정해야 한다") {
            val response = MockHttpServletResponse()
            val wrapper = SvnServletResponseWrapper(response)

            wrapper.sendError(404)

            response.status shouldBe 404
            // MockHttpServletResponse는 sendError()가 실제로 호출되면 errorMessage/committed 등의
            // 상태를 별도로 기록한다 — setStatus() 경로를 탔다면 이 표식들이 비어 있어야 한다.
            response.errorMessage shouldBe null
        }
    }

    describe("sendError(sc, msg)") {
        it("메시지가 있어도 컨테이너의 에러 페이지 재-dispatch를 트리거하지 않고 상태 코드만 설정해야 한다") {
            val response = MockHttpServletResponse()
            val wrapper = SvnServletResponseWrapper(response)

            wrapper.sendError(500, "내부 오류")

            response.status shouldBe 500
            response.errorMessage shouldBe null
        }
    }
})
