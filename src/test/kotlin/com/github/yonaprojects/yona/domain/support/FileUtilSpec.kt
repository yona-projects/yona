package com.github.yonaprojects.yona.domain.support

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class FileUtilSpec : DescribeSpec({
    describe("detectCharset(ByteArray)") {
        it("빈 배열이면 감지 실패로 기본값 UTF-8을 반환해야 한다") {
            FileUtil.detectCharset(ByteArray(0)) shouldBe "UTF-8"
        }

        it("짧은 UTF-8 텍스트는 do-while 루프를 1회만 순회하며 감지해야 한다") {
            val bytes = "hello world 안녕하세요".toByteArray(StandardCharsets.UTF_8)
            val result = FileUtil.detectCharset(bytes)
            result.isNotBlank() shouldBe true
        }

        it("4096바이트를 초과하는 긴 텍스트는 do-while 루프를 여러 번 순회해야 한다") {
            val bytes = ("안녕하세요, 이것은 반복 테스트를 위한 긴 문자열입니다. ".repeat(500))
                .toByteArray(StandardCharsets.UTF_8)
            (bytes.size > 4096) shouldBe true
            val result = FileUtil.detectCharset(bytes)
            result.isNotBlank() shouldBe true
        }
    }

    describe("detectCharset(InputStream)") {
        it("빈 스트림이면(즉시 EOF) while 루프 본문을 한 번도 돌지 않고 기본값 UTF-8을 반환해야 한다") {
            val result = FileUtil.detectCharset(ByteArrayInputStream(ByteArray(0)))
            result shouldBe "UTF-8"
        }

        it("짧은 스트림은 while 루프를 순회하며 감지해야 한다") {
            val bytes = "hello world 안녕하세요".toByteArray(StandardCharsets.UTF_8)
            val result = FileUtil.detectCharset(ByteArrayInputStream(bytes))
            result.isNotBlank() shouldBe true
        }

        it("4096바이트를 초과하는 스트림은 while 루프를 여러 번 순회해야 한다") {
            val bytes = ("안녕하세요, 이것은 반복 테스트를 위한 긴 문자열입니다. ".repeat(500))
                .toByteArray(StandardCharsets.UTF_8)
            (bytes.size > 4096) shouldBe true
            val result = FileUtil.detectCharset(ByteArrayInputStream(bytes))
            result.isNotBlank() shouldBe true
        }
    }

    describe("detectMediaType()") {
        it("일반 텍스트 파일은 text/plain 계열로 감지하고 charset을 덧붙여야 한다") {
            val tempFile = Files.createTempFile("fileutil-test", ".txt").toFile()
            tempFile.writeText("plain ascii text content", StandardCharsets.UTF_8)

            try {
                val result = FileUtil.detectMediaType(tempFile, tempFile.name)
                result shouldContain "text/plain"
                result shouldContain "charset="
            } finally {
                tempFile.delete()
            }
        }

        it("바이너리(텍스트도 ogv 오판 대상도 아닌) 파일은 else 분기로 감지된 타입을 그대로 반환해야 한다") {
            val tempFile = Files.createTempFile("fileutil-test", ".png").toFile()
            // PNG 매직 바이트
            tempFile.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

            try {
                val result = FileUtil.detectMediaType(tempFile, tempFile.name)
                result shouldBe "image/png"
            } finally {
                tempFile.delete()
            }
        }

        // mediaType.toString() == "audio/ogg" && name가 ".ogv"로 끝남 (line 58) 분기는 구조적으로
        // 도달 불가능함을 Apache Tika 2.9.2의 tika-mimetypes.xml로 직접 확인했다:
        // - "audio/ogg"는 자체 매직바이트 패턴이 전혀 없고 오직 파일명 글롭 "*.oga"로만 매치된다
        //   (Tika().detector.detect()의 매직바이트 감지 경로로는 절대 "audio/ogg" 자체가 나오지
        //   않는다 — 실제로 유효한 Ogg/Vorbis 매직 바이트를 직접 구성해 검증한 결과 Tika는 더
        //   구체적인 서브타입 "audio/vorbis"를 반환했다. 최상위 "OggS" 매직(우선순위 50)의
        //   기본 폴백은 "application/ogg"이지 "audio/ogg"가 아니다).
        // - "audio/ogg"가 나오려면 파일명이 ".oga"로 끝나야 글롭이 매치되는데, 같은 조건의 우변은
        //   파일명이 ".ogv"로 끝날 것을 요구한다 — 한 파일명이 ".oga"와 ".ogv"로 동시에 끝날 수는
        //   없으므로 두 조건이 상호 배타적이다.
    }
})
