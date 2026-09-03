package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.File
import java.io.IOException
import java.nio.file.Files
import jakarta.servlet.ReadListener
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

class LfsStorageControllerSpec : DescribeSpec({
    val tempDir = Files.createTempDirectory("yona-lfs-test").toFile()
    val controller = LfsStorageController(tempDir.absolutePath)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    afterSpec { tempDir.deleteRecursively() }

    describe("GET /git-lfs/{owner}/{project}/objects/{oid}") {
        it("oid 길이가 4 미만이면 400을 반환해야 한다") {
            mockMvc.perform(get("/git-lfs/owner/proj/objects/abc"))
                .andExpect(status().isBadRequest)
        }

        it("파일이 존재하지 않으면 404를 반환해야 한다") {
            mockMvc.perform(get("/git-lfs/owner/proj/objects/aabbccdd0000"))
                .andExpect(status().isNotFound)
        }

        it("경로가 파일이 아니라 디렉터리면 404를 반환해야 한다") {
            val dirPath = File(tempDir, "owner/proj/objects/aa/bb/aabbccdd1111")
            dirPath.mkdirs()

            mockMvc.perform(get("/git-lfs/owner/proj/objects/aabbccdd1111"))
                .andExpect(status().isNotFound)
        }

        it("파일이 존재하면 200과 함께 바이너리를 반환해야 한다") {
            val oid = "aabbccdd2222"
            val objectFile = File(tempDir, "owner/proj/objects/aa/bb/$oid")
            objectFile.parentFile.mkdirs()
            objectFile.writeBytes(byteArrayOf(1, 2, 3))

            mockMvc.perform(get("/git-lfs/owner/proj/objects/$oid"))
                .andExpect(status().isOk)
                .andExpect(MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"$oid\""))
        }
    }

    describe("PUT /git-lfs/{owner}/{project}/objects/{oid}") {
        it("oid 길이가 4 미만이면 400을 반환해야 한다") {
            mockMvc.perform(put("/git-lfs/owner/proj/objects/abc").content(byteArrayOf(1)))
                .andExpect(status().isBadRequest)
        }

        it("정상적으로 업로드하면 201을 반환하고 파일이 저장되어야 한다") {
            val oid = "aabbccdd3333"
            mockMvc.perform(put("/git-lfs/owner/proj/objects/$oid").content(byteArrayOf(9, 8, 7)))
                .andExpect(status().isCreated)

            val saved = File(tempDir, "owner/proj/objects/aa/bb/$oid")
            saved.exists() shouldBe true
            saved.readBytes() shouldBe byteArrayOf(9, 8, 7)
        }

        it("파일 생성 자체가 실패해 대상 파일이 존재하지 않으면 삭제를 시도하지 않고 500을 반환해야 한다") {
            val oid = "112233445566"
            // "22" 자리에 디렉터리 대신 일반 파일을 미리 만들어 두면, FileOutputStream(file) 생성 자체가
            // 실패하고(부모가 디렉터리가 아님) 대상 파일(11/22/oid)은 애초에 존재할 수 없는 경로가 되어
            // catch 블록의 file.exists() == false 분기를 타게 된다.
            // (다른 테스트들은 모두 "aabbccdd" 접두사를 써서 aa/bb 하위 디렉터리를 공유하므로,
            // 이 테스트만 별도 접두사를 사용해 tempDir을 공유하는 다른 테스트와의 경로 충돌을 피한다.)
            val conflictingFile = File(tempDir, "owner/proj/objects/11/22")
            conflictingFile.parentFile.mkdirs()
            conflictingFile.writeText("not a directory")

            val request = mockk<HttpServletRequest>()
            val stream = object : ServletInputStream() {
                override fun isFinished() = true
                override fun isReady() = true
                override fun setReadListener(readListener: ReadListener?) {}
                override fun read(): Int = -1
            }
            every { request.inputStream } returns stream

            val result = controller.uploadObject("owner", "proj", oid, request)

            result.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
            File(tempDir, "owner/proj/objects/11/22/$oid").exists() shouldBe false
        }

        it("업로드 중 예외가 발생하면 500을 반환하고, 이미 생성된 파일이 있으면 삭제해야 한다") {
            val oid = "aabbccdd4444"
            val brokenRequest = mockk<HttpServletRequest>()
            val brokenStream = object : ServletInputStream() {
                override fun isFinished() = false
                override fun isReady() = true
                override fun setReadListener(readListener: ReadListener?) {}
                override fun read(): Int = throw IOException("boom")
            }
            every { brokenRequest.inputStream } returns brokenStream

            val result = controller.uploadObject("owner", "proj", oid, brokenRequest)

            result.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
            File(tempDir, "owner/proj/objects/aa/bb/$oid").exists() shouldBe false
        }
    }
})
