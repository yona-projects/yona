package com.github.yonaprojects.yona.domain.support

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// TranslationServiceImpl의 translationApi/translationHeaderKey/translationHeaderValue는 @Value로
// 주입되는 lateinit 필드이고, httpClient는 생성자 주입 없이 필드 초기화식에서 직접
// HttpClient.newHttpClient()로 생성된다. Spring 컨텍스트 없이 단위테스트하기 위해, 기존 코드베이스의
// 관례(ImapMailboxPollerSpec 등)와 동일하게 리플렉션으로 private 필드를 직접 세팅/치환한다.
private fun TranslationServiceImpl.setField(name: String, value: Any?) {
    val field = TranslationServiceImpl::class.java.getDeclaredField(name)
    field.isAccessible = true
    field.set(this, value)
}

// private 메서드(merge/callExternalApi)를 리플렉션으로 직접 호출한다. 도달 불가능하지 않은 분기까지
// 개별적으로 검증하기 위함이다. InvocationTargetException은 원래 예외를 벗겨서 던진다.
private fun TranslationServiceImpl.callPrivate(name: String, paramTypes: Array<Class<*>>, vararg args: Any?): Any? {
    val method = TranslationServiceImpl::class.java.getDeclaredMethod(name, *paramTypes)
    method.isAccessible = true
    return try {
        method.invoke(this, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}

private fun mockHttpResponse(status: Int, body: String): HttpResponse<String> {
    val response = mockk<HttpResponse<String>>()
    every { response.statusCode() } returns status
    every { response.body() } returns body
    return response
}

private fun newTranslationService(
    api: String = "https://translate.example.com/api",
    headerKey: String = "",
    headerValue: String = ""
): Pair<TranslationServiceImpl, HttpClient> {
    val mockHttpClient = mockk<HttpClient>()
    val service = TranslationServiceImpl(ObjectMapper())
    service.setField("translationApi", api)
    service.setField("translationHeaderKey", headerKey)
    service.setField("translationHeaderValue", headerValue)
    service.setField("httpClient", mockHttpClient)
    return service to mockHttpClient
}

// yona 레거시 Google Translate 연동(번역 API 호출/청크 분할/병합) 대응.
class TranslationServiceImplSpec : DescribeSpec({

    describe("translate") {
        it("translation API URL이 설정되지 않으면 경고 로그만 남기고 null을 반환한다") {
            val (service, httpClient) = newTranslationService(api = "")

            val result = service.translate("안녕하세요")

            result shouldBe null
            verify(exactly = 0) { httpClient.send<String>(any(), any()) }
        }

        it("빈 문자열을 번역 요청하면 API를 호출하지 않고 빈 문자열을 그대로 반환한다") {
            val (service, httpClient) = newTranslationService()

            val result = service.translate("")

            result shouldBe ""
            verify(exactly = 0) { httpClient.send<String>(any(), any()) }
        }

        it("정상 응답이면 번역된 텍스트를 반환한다") {
            val (service, httpClient) = newTranslationService()
            every { httpClient.send<String>(any(), any()) } returns
                mockHttpResponse(200, """{"result":{"translatedText":"Hello"}}""")

            val result = service.translate("안녕")

            result shouldBe "Hello"
        }

        it("긴 텍스트와 빈 줄이 섞이면 빈 청크는 API 호출 없이 개행으로 대체되고 내용이 있는 청크만 번역 API를 호출한다") {
            val (service, httpClient) = newTranslationService()
            every { httpClient.send<String>(any(), any()) } returns
                mockHttpResponse(200, """{"result":{"translatedText":"번역됨"}}""")
            // limit(4500)에 걸리는 긴 줄 뒤에 빈 줄 두 개를 붙여, merge() 결과가
            // ["", "X...\r\n", "\r\n\r\n"] 세 청크로 쪼개지도록 구성한다 — 앞/뒤 청크는 공백뿐이라
            // translate()의 chunk.isBlank() 분기(개행 대체, API 미호출)를, 가운데 청크는 반대 분기를 검증한다.
            val text = "X".repeat(4500) + "\n\n"

            val result = service.translate(text)

            result shouldNotBe null
            result!! shouldContain "번역됨"
            verify(exactly = 1) { httpClient.send<String>(any(), any()) }
        }

        it("callExternalApi가 실패하면 이후 청크를 처리하지 않고 즉시 null을 반환한다") {
            val (service, httpClient) = newTranslationService()
            every { httpClient.send<String>(any(), any()) } returns mockHttpResponse(500, "error")
            // 3000자 두 줄은 각각 limit(4500)을 넘지 않지만 합치면 넘어가므로 서로 다른 청크로 분리된다.
            val text = "A".repeat(3000) + "\n" + "B".repeat(3000)

            val result = service.translate(text)

            result shouldBe null
            verify(exactly = 1) { httpClient.send<String>(any(), any()) }
        }
    }

    describe("merge (private)") {
        it("누적 길이가 limit 미만이면 하나의 청크로 합친다") {
            val (service, _) = newTranslationService()

            @Suppress("UNCHECKED_CAST")
            val result = service.callPrivate("merge", arrayOf(List::class.java), listOf("a", "b", "c")) as List<String>

            result.size shouldBe 1
            result[0] shouldBe "a\r\nb\r\nc\r\n"
        }

        it("누적 길이가 limit 이상이면 새 청크로 분리한다") {
            val (service, _) = newTranslationService()
            val a = "x".repeat(3000)
            val b = "y".repeat(3000)

            @Suppress("UNCHECKED_CAST")
            val result = service.callPrivate("merge", arrayOf(List::class.java), listOf(a, b)) as List<String>

            result.size shouldBe 2
            result[0] shouldBe "$a\r\n"
            result[1] shouldBe "$b\r\n"
        }

        it("빈 리스트를 넘기면 빈 리스트를 반환한다") {
            val (service, _) = newTranslationService()

            @Suppress("UNCHECKED_CAST")
            val result = service.callPrivate("merge", arrayOf(List::class.java), emptyList<String>()) as List<String>

            result shouldBe emptyList<String>()
        }
    }

    describe("callExternalApi (private)") {
        it("헤더 키/값이 모두 비어있으면 커스텀 헤더를 추가하지 않는다") {
            val (service, httpClient) = newTranslationService(headerKey = "", headerValue = "")
            val requestSlot = slot<HttpRequest>()
            every { httpClient.send<String>(capture(requestSlot), any()) } returns
                mockHttpResponse(200, """{"result":{"translatedText":"ok"}}""")

            service.callPrivate("callExternalApi", arrayOf(String::class.java), "chunk")

            requestSlot.captured.headers().firstValue("X-Custom").isPresent shouldBe false
        }

        it("헤더 키만 있고 값이 비어있으면 커스텀 헤더를 추가하지 않는다") {
            val (service, httpClient) = newTranslationService(headerKey = "X-Custom", headerValue = "")
            val requestSlot = slot<HttpRequest>()
            every { httpClient.send<String>(capture(requestSlot), any()) } returns
                mockHttpResponse(200, """{"result":{"translatedText":"ok"}}""")

            service.callPrivate("callExternalApi", arrayOf(String::class.java), "chunk")

            requestSlot.captured.headers().firstValue("X-Custom").isPresent shouldBe false
        }

        it("헤더 키가 비어있고 값만 있으면 커스텀 헤더를 추가하지 않는다") {
            val (service, httpClient) = newTranslationService(headerKey = "", headerValue = "secret")
            val requestSlot = slot<HttpRequest>()
            every { httpClient.send<String>(capture(requestSlot), any()) } returns
                mockHttpResponse(200, """{"result":{"translatedText":"ok"}}""")

            service.callPrivate("callExternalApi", arrayOf(String::class.java), "chunk")

            requestSlot.captured.headers().map().values.flatten().contains("secret") shouldBe false
        }

        it("헤더 키/값이 모두 있으면 커스텀 헤더를 추가한다") {
            val (service, httpClient) = newTranslationService(headerKey = "X-Custom", headerValue = "secret")
            val requestSlot = slot<HttpRequest>()
            every { httpClient.send<String>(capture(requestSlot), any()) } returns
                mockHttpResponse(200, """{"result":{"translatedText":"ok"}}""")

            service.callPrivate("callExternalApi", arrayOf(String::class.java), "chunk")

            requestSlot.captured.headers().firstValue("X-Custom").get() shouldBe "secret"
        }

        it("상태코드가 200이 아니면 null을 반환한다") {
            val (service, httpClient) = newTranslationService()
            every { httpClient.send<String>(any(), any()) } returns mockHttpResponse(500, "error body")

            val result = service.callPrivate("callExternalApi", arrayOf(String::class.java), "chunk")

            result shouldBe null
        }

        it("translatedText 필드가 없으면 null을 반환한다") {
            val (service, httpClient) = newTranslationService()
            every { httpClient.send<String>(any(), any()) } returns
                mockHttpResponse(200, """{"result":{}}""")

            val result = service.callPrivate("callExternalApi", arrayOf(String::class.java), "chunk")

            result shouldBe null
        }

        it("translatedText 필드가 있으면 값을 반환한다") {
            val (service, httpClient) = newTranslationService()
            every { httpClient.send<String>(any(), any()) } returns
                mockHttpResponse(200, """{"result":{"translatedText":"안녕하세요"}}""")

            val result = service.callPrivate("callExternalApi", arrayOf(String::class.java), "chunk")

            result shouldBe "안녕하세요"
        }

        it("HTTP 호출 중 예외가 발생하면 null을 반환한다") {
            val (service, httpClient) = newTranslationService()
            every { httpClient.send<String>(any(), any()) } throws IOException("timeout")

            val result = service.callPrivate("callExternalApi", arrayOf(String::class.java), "chunk")

            result shouldBe null
        }
    }
})
