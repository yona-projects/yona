package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain

// 빈 저장소(code/nohead.html) 안내의 clone URL은 JS가 조립한다. 실제 git smart-HTTP 서블릿은 "/git/*"에 등록돼
// 있으므로(GitServletConfig) 안내 URL도 "/git/{owner}/{name}.git" 형태여야 한다 (TemplateHelper의 clone URL과 동일).
class NoheadCloneUrlSpec : DescribeSpec({
    describe("code/nohead.html clone URL 안내") {
        val html = NoheadCloneUrlSpec::class.java.getResource("/templates/code/nohead.html")!!.readText()

        it("/git/ 접두를 포함한 URL로 조립해야 한다") {
            html shouldContain "\"/git/\" + owner + \"/\" + name"
        }
    }
})
