package com.github.yonaprojects.yona.domain.project

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// v1.6 Project.java:55-58 `@Pattern("^[a-zA-Z0-9-_\\.가-힣]+$")` + `@Restricted({".", "..", ".git"})` — 서버에서 폼 바인딩 때 적용돼
// `/`가 든 이름은 거부됐다. 저장소 경로(File(baseDir, "$owner/$name.git"))로 조립되므로 형식 검증이 없으면 경로 이탈이 가능하다.
class ProjectNameValidatorSpec : DescribeSpec({
    describe("ProjectNameValidator.isValidFormat") {
        for (ok in listOf("yona", "my-proj_1.0", "한글-이름", "a.b", "A..B", "x")) {
            it("'$ok'는 허용한다") { ProjectNameValidator.isValidFormat(ok) shouldBe true }
        }
        for (bad in listOf("", " ", "a/b", "a\\b", "../x", "x y", "a;b", "a\nb", "a\u0000b", "<b>x</b>", "a%2Fb")) {
            it("'${bad.replace("\n", "\\n").replace("\u0000", "\\0")}'는 거부한다") { ProjectNameValidator.isValidFormat(bad) shouldBe false }
        }
    }
})
