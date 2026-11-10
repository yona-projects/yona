package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 화면이 쓰는 JS 라이브러리의 버전과 템플릿의 참조를 고정한다(JS 라이브러리 업그레이드, `decisions.md` D-91, `architecture/js-upgrade-plan.md`).
 * 라이브러리를 올리고 이 시험의 기준을 고치지 않거나, 올리지 않은 라이브러리가 몰래 바뀌면 실패한다.
 *
 * - jQuery는 3.7.1이다(4.0은 하지 않기로 했다). 주 화면의 `yona-layout.js` 번들, 마이그레이션과 첫 설정 화면의 단독 파일이 모두 같은 버전이다.
 * - jQuery UI는 효과만 담은 1.14.2 맞춤 묶음이다.
 */
class JsLibraryVersionsTest {

    private val assets = File("src/main/resources/static/assets")
    private val templates = File("src/main/resources/templates")

    private fun asset(path: String) = File(assets, path)

    private fun template(path: String) = File(templates, path).readText()

    private fun templateFiles() = templates.walkTopDown().filter { it.isFile && it.extension == "html" }.toList()

    @Test
    fun `jQuery는 3_7_1 파일이다`() {
        val file = asset("javascripts/lib/jquery/jquery-3.7.1.js")
        assertTrue(file.isFile, "jquery-3.7.1.js가 있어야 한다")
        assertTrue(file.readText().take(400).contains("jQuery JavaScript Library v3.7.1"), file.readText().take(200))
    }

    @Test
    fun `레이아웃 번들 yona-layout_js의 jQuery는 3_7_1이다`() {
        val bundle = asset("javascripts/yona-layout.js").readText()
        // 압축기에 따라 따옴표 종류(", ', `)가 달라지므로 종류를 가리지 않고 버전 문자열을 찾는다.
        assertTrue(Regex("[\"'`]3\\.7\\.1[\"'`]").containsMatchIn(bundle), "번들에 jQuery 3.7.1의 버전 문자열이 있어야 한다")
        assertTrue(!Regex("[\"'`]3\\.3\\.1[\"'`]").containsMatchIn(bundle), "옛 jQuery 3.3.1이 번들에 남아 있으면 안 된다")
    }

    @Test
    fun `jQuery를 불러오는 템플릿은 모두 3_7_1 파일을 쓴다`() {
        val pattern = Regex("""jquery-(\d+\.\d+\.\d+)\.js""")
        val refs = templateFiles().flatMap { f -> pattern.findAll(f.readText()).map { f.name to it.groupValues[1] }.toList() }
        assertTrue(refs.isNotEmpty(), "jQuery를 단독으로 불러오는 템플릿이 있어야 한다(마이그레이션과 첫 설정 화면)")
        assertEquals(emptyList(), refs.filter { it.second != "3.7.1" }, "3.7.1이 아닌 jQuery를 불러오는 템플릿")
    }

    @Test
    fun `jQuery UI는 1_14_2 효과 묶음이고 템플릿은 그것만 쓴다`() {
        val file = asset("javascripts/lib/jquery/jquery-ui-1.14.2.custom.min.js")
        assertTrue(file.isFile, "jquery-ui-1.14.2.custom.min.js가 있어야 한다")
        val head = file.readText().take(600)
        assertTrue(head.contains("jQuery UI - v1.14.2"), head)
        for (effect in listOf("effect.js", "effect-blind.js", "effect-bounce.js", "effect-drop.js", "effect-fade.js", "effect-fold.js", "effect-highlight.js", "effect-shake.js", "effect-transfer.js")) {
            assertTrue(head.contains(effect), "효과 묶음에 $effect 가 들어 있어야 한다: $head")
        }
        val users = templateFiles().filter { it.readText().contains("jquery-ui") }
        assertTrue(users.isNotEmpty())
        users.forEach { assertTrue(it.readText().contains("jquery-ui-1.14.2.custom.min.js") && !it.readText().contains("jquery-ui-1.10.4"), "${it.name}이 1.14.2 묶음을 쓰지 않는다") }
    }

    @Test
    fun `템플릿이 참조하는 JS 파일은 모두 실제로 있다`() {
        // 스타일시트는 빌드에서 SCSS로 만들어지고 이미지는 별도이므로 JS(`javascripts/`, `bootstrap/js/`)만 본다. 파일 이름을 바꿀 때 404가 나는 것을 막는다
        val pattern = Regex("""@\{/assets/((?:javascripts|bootstrap/js)/[^}]+\.js)}""")
        val missing = templateFiles().flatMap { f -> pattern.findAll(f.readText()).map { f.name to it.groupValues[1] }.toList() }
            .filter { (_, path) -> !File(assets, path).isFile }
            .distinct()
        assertEquals(emptyList(), missing, "템플릿이 가리키지만 없는 JS 파일")
    }

    @Test
    fun `벤더 파일은 기준 목록의 공식 배포 파일과 byte 단위로 같다`() {
        val manifest = File("src/test/resources/js-vendor-manifest.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        assertTrue(manifest.size >= 10, "기준 목록이 비어 있다")
        val wrong = manifest.filter { (path, _, _, sha) ->
            val file = File(assets, "javascripts/lib/$path")
            !file.isFile || MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) } != sha
        }.map { "${it[0]} (${it[1]} ${it[2]})" }
        assertEquals(emptyList(), wrong, "기준 목록과 다른 벤더 파일(올렸다면 기준 목록도 고친다)")
    }

    @Test
    fun `레이아웃 번들의 NProgress는 0_2_0이다`() {
        val bundle = asset("javascripts/yona-layout.js").readText()
        assertTrue(Regex("version\\s*[:=]\\s*[\"'`]0\\.2\\.0[\"'`]").containsMatchIn(bundle), "번들에 NProgress 0.2.0 표기가 있어야 한다")
        assertTrue(!bundle.contains("0.1.6"), "옛 NProgress 0.1.6이 남아 있으면 안 된다")
    }

    @Test
    fun `moment는 모든 로케일 묶음 2_31_0을 쓰고 옛 lang 호출이 없다`() {
        val users = templateFiles().filter { it.readText().contains("moment") }
        assertTrue(users.isNotEmpty())
        users.forEach { assertTrue(!it.readText().contains("moment-with-langs"), "${it.name}이 옛 moment 파일을 가리킨다") }
        assertTrue(templateFiles().none { it.readText().contains("moment.lang(") }, "moment 2.8부터 lang은 locale로 바뀌었다")
        assertTrue(templateFiles().any { it.readText().contains("moment-with-locales.min.js") })
    }

    @Test
    fun `쓰이지 않는 옛 벤더 파일은 저장소에 없다`() {
        // 참조를 검사해 지운 파일이다(D-91, A-393). 템플릿, JS, 번들 입력 목록, Kotlin 어디에서도 불러오지 않는다. v1.6 원본 사본은 `public/`에 그대로 있다
        val removed = listOf(
            "jquery/jquery-1.7.2.js", "jquery/jquery-1.7.2.min.js", "jquery/jquery-1.9.0.js", "jquery/jquery-3.3.1.js",
            "jquery/jquery-ui-1.10.4.custom.min.js", "jquery/jquery-ui.custom.js", "moment-with-langs.min.js", "underscore.js",
            "magnific-popup/jquery.magnific-popup.js", "magnific-popup/jquery.magnific-popup.min.js", "jquery/jquery.dynatree.js",
            "jquery/jquery.event.drag-2.2.js", "jquery/jquery.validate.min.js", "jquery/jquery.scrollTo.js",
            "jquery/jquery.zclip.min.js", "jquery/ZeroClipboard.swf",
            // 번들 도구 교체 뒤 쓰이지 않는 것으로 확인해 지웠다(D-92, A-398)
            "jquery/jquery.validate.js", "jquery/jquery.placeholder.min.js"
        )
        assertEquals(emptyList(), removed.filter { File(assets, "javascripts/lib/$it").exists() }, "지웠던 파일이 다시 들어왔다")
    }

    @Test
    fun `복사 단추는 Flash 기반 zclip을 쓰지 않는다`() {
        // 번들(yona-common.js)은 재빌드하지 않아 안의 옛 코드는 보지 않는다. 템플릿과 번들 밖의 JS, 번들 입력 목록만 본다(A-394)
        val js = listOf("common", "service", "esm", "template").flatMap { File(assets, "javascripts/$it").walkTopDown().filter { f -> f.isFile && f.extension == "js" }.toList() }
        val users = (templateFiles() + js + File("build-tools/minify-js.sh")).filter { Regex("zclip|ZeroClipboard", RegexOption.IGNORE_CASE).containsMatchIn(it.readText()) }.map { it.name }
        assertEquals(emptyList(), users, "Flash 기반 복사(zclip)를 아직 쓰는 파일")
    }

    @Test
    fun `복사는 yona_Clipboard 도우미를 거치고 화면이 그것을 불러온다`() {
        val helper = File(assets, "javascripts/common/yona.Clipboard.js")
        assertTrue(helper.isFile, "yona.Clipboard.js가 있어야 한다")
        val text = helper.readText()
        assertTrue(text.contains("navigator.clipboard") && text.contains("isSecureContext") && text.contains("execCommand"), "클립보드 API를 먼저 쓰고 보안 연결이 아니면 대체 수단을 쓴다")
        assertTrue(File(templates, "common/scripts.html").readText().contains("common/yona.Clipboard.js"), "scripts.html이 도우미를 불러와야 한다")
        assertTrue(File(templates, "code/history.html").readText().contains("yona.Clipboard.copy"), "커밋 번호 복사가 도우미를 써야 한다")
        assertTrue(File(assets, "javascripts/esm/service/project.Home.js").readText().contains("yona.Clipboard.copy"), "저장소 주소 복사가 도우미를 써야 한다")
    }

    @Test
    fun `번들 입력 목록 bundles_mjs의 파일이 모두 있다`() {
        val text = java.io.File("build-tools/js-bundle/bundles.mjs").readText()
        val inputs = Regex("""`\$\{(JS|BS)}/([^`]+)`""").findAll(text).map {
            (if (it.groupValues[1] == "JS") "javascripts/" else "bootstrap/js/") + it.groupValues[2]
        }.toList()
        assertTrue(inputs.size >= 20, "입력이 너무 적다: ${inputs.size}")
        assertEquals(emptyList(), inputs.filterNot { asset(it).isFile }, "없는 번들 입력 파일")
        assertTrue(!java.io.File("build-tools/minify-js.sh").readText().contains("compiler.jar"), "번들은 vite로 만든다(D-92)")
    }

    @Test
    fun `yona Interval은 없고 풀 리퀘스트 보기가 입력 없음 규칙을 직접 가진다`() {
        val assets = java.io.File("src/main/resources/static/assets/javascripts")
        assertTrue(!java.io.File(assets, "common/yona.Interval.js").exists(), "yona.Interval.js는 지워졌다(S-02, A-416)")
        assertEquals(emptyList(), templateFiles().filter { it.readText().contains("yona.Interval") }.map { it.path })
        val view = java.io.File(assets, "esm/service/git.View.js").readText()
        assertTrue(!view.contains("yona.Interval."), "풀 리퀘스트 보기가 yona.Interval을 부르지 않는다")
        assertTrue(view.contains("nIdleLimit"), "입력 없음 규칙이 풀 리퀘스트 보기 안에 있다")
    }

    @Test
    fun `clipboard js는 없고 복사는 yona Clipboard 도우미 하나만 쓰며 쓰이지 않는 validate와 placeholder 플러그인은 번들에 없다`() {
        // clipboard.js 2.0.11은 이슈 보기의 메일 복사 단추만 썼다. 그 단추도 yona.Clipboard(표준 navigator.clipboard, 대체 경로 execCommand)로 옮겨 라이브러리를 뺐다(S-01).
        assertTrue(!asset("javascripts/lib/clipboard.js").exists(), "clipboard.js는 지웠다")
        val inputs = java.io.File("build-tools/js-bundle/bundles.mjs").readText()
        assertTrue(!inputs.contains("clipboard.js"), "번들 입력에서 빠져야 한다")
        assertTrue(!inputs.contains("jquery.validate.js") && !inputs.contains("jquery.placeholder"), "번들 입력에서 빠져야 한다")
        val usesClipboardJs = templateFiles().filter { it.readText().contains("ClipboardJS") }.map { it.path }
        assertEquals(emptyList(), usesClipboardJs, "ClipboardJS를 부르는 템플릿")
        assertTrue(template("issue/view.html").contains("yona.Clipboard.copy"), "이슈 보기의 메일 복사 단추는 도우미를 쓴다")
        // clipboard.js가 읽던 data-clipboard-target은 읽는 곳이 없어졌다(프로젝트 홈의 복사 단추는 yona.project.Home.js가 id로 연결한다). data-copy-text는 이슈 보기가 읽는다.
        val inertTarget = templateFiles().filter { it.readText().contains("data-clipboard-target") }.map { it.path }
        assertEquals(emptyList(), inertTarget, "아무 동작도 하지 않는 data-clipboard-target이 남은 템플릿")
        assertTrue(template("issue/partial-voter-list.html").contains("data-copy-text"), "메일 복사 문구는 data-copy-text로 넘긴다")
        val oldName = templateFiles().filter { it.readText().contains("data-clipboard-text") }.map { it.path }
        assertEquals(emptyList(), oldName, "clipboard.js 시절의 속성 이름 data-clipboard-text가 남은 템플릿")
        // 현대 브라우저는 placeholder 속성을 기본 지원하므로 폴리필 초기화 호출도 없다
        assertTrue(!java.io.File("src/main/resources/templates/common/scripts.html").readText().contains(".placeholder("), "placeholder 폴리필 호출이 없어야 한다")
    }

    @Test
    fun `번들 도구 교체 뒤 옛 Closure 도구 파일은 없다`() {
        assertTrue(!File(assets, "compiler.jar").exists(), "static/assets/compiler.jar는 지웠다(D-92, A-398)")
        assertTrue(!java.io.File("minify-js.sh").exists(), "저장소 루트의 옛 minify-js.sh는 지웠다")
    }

    @Test
    fun `달력은 vanilla-calendar-pro 3_4_0이고 Pikaday는 파일도 참조도 없다`() {
        // S-09, A-424: 날짜 선택 위젯을 Pikaday(저장소가 보관 처리됨)에서 vanilla-calendar-pro로 바꿨다. 계약은 yona.ui.Calendar.js가 지킨다.
        val js = asset("javascripts/lib/vanilla-calendar-pro/index.js")
        assertTrue(js.isFile && js.readText().take(200).contains("vanilla-calendar-pro v3.4.0"), "vanilla-calendar-pro 3.4.0 파일이 있어야 한다")
        assertTrue(asset("javascripts/lib/vanilla-calendar-pro/index.css").readText().take(200).contains("vanilla-calendar-pro v3.4.0"))
        assertTrue(asset("javascripts/lib/vanilla-calendar-pro/LICENSE").readText().contains("MIT License"))
        assertTrue(!asset("javascripts/lib/pikaday").exists(), "lib/pikaday는 지웠다")
        assertEquals(emptyList(), templateFiles().filter { it.readText().contains("pikaday", ignoreCase = true) }.map { it.path }, "Pikaday를 부르는 템플릿")
        for (layout in listOf("layout/layout.html", "layout/layout-framed.html", "layout/migration-layout.html"))
            assertTrue(template(layout).contains("lib/vanilla-calendar-pro/index.css"), "$layout 은 달력 CSS를 불러야 한다")
        val calendar = template("common/calendar.html")
        assertTrue(calendar.contains("lib/vanilla-calendar-pro/index.js") && calendar.contains("common/yona.ui.Calendar.js"), "공통 달력 조각은 라이브러리와 래퍼를 불러야 한다")
        // 항상 보이는 달력을 쓰는 화면(마일스톤 작성과 수정, API 토큰)은 래퍼도 불러야 하고, moment는 Pikaday 때문에 불렀던 것이라 더 부르지 않는다
        for (page in listOf("milestone/create.html", "milestone/edit.html", "user/partial-token-form.html")) {
            val html = template(page)
            assertTrue(html.contains("lib/vanilla-calendar-pro/index.js") && html.contains("common/yona.ui.Calendar.js"), "$page 는 달력 라이브러리와 래퍼를 불러야 한다")
            assertTrue(!html.contains("moment"), "$page 는 moment를 부르지 않는다")
        }
        assertTrue(template("user/partial-token-form.html").contains("yona.ui.Calendar.inline"), "API 토큰 화면은 항상 보이는 달력 도우미를 쓴다")
        assertTrue(asset("javascripts/esm/service/milestone.Write.js").readText().contains("yona.ui.Calendar.inline"), "마일스톤 작성은 항상 보이는 달력 도우미를 쓴다")
        assertTrue(!asset("javascripts/esm/service/milestone.Write.js").readText().contains("Pikaday"))
        val notice = java.io.File("NOTICE").readText()
        assertTrue(notice.contains("vanilla-calendar-pro") && notice.contains("MIT"), "NOTICE에 vanilla-calendar-pro의 저작권 고지가 있어야 한다")
    }

    @Test
    fun `레이아웃 번들의 xssClean은 표준 Sanitizer API를 먼저 쓰고 옛 필터를 대체 경로로 둔다`() {
        // S-12, A-425: 브라우저가 Element.setHTML을 지원하면 표준으로 정화하고 지원하지 않으면 lib/xss.js의 옛 필터를 쓴다. 번들이 소스를 따라 다시 만들어졌는지 확인한다.
        val bundle = asset("javascripts/yona-layout.js").readText()
        assertTrue(bundle.contains("setHTML") && bundle.contains("Sanitizer"), "번들에 표준 정화 경로가 있어야 한다")
        assertTrue(bundle.contains("http://www.w3.org/1998/Math/MathML") && bundle.contains("http://www.w3.org/2000/svg"), "math와 svg는 이름 공간으로 걷어내야 한다")
        val bundles = listOf("yona-layout.js", "yona-common.js", "yona-lib.js").joinToString("\n") { asset("javascripts/$it").readText() }
        assertTrue(Regex("defence").findAll(bundles).count() >= 2, "옛 필터(Filter.defence)의 정의와 대체 경로의 호출이 번들에 있어야 한다")
    }

    @Test
    fun `자동 완성은 BS2 typeahead 플러그인 없이 yona_ui_Typeahead 하나로 동작한다`() {
        // S-10, A-426: bootstrap-typeahead(2.3.1)와 bootstrap-better-typeahead 플러그인과 그것에 기대던 호출을 걷어냈다.
        assertTrue(!asset("bootstrap/js/bootstrap-typeahead.js").exists() && !asset("bootstrap/js/bootstrap-better-typeahead.js").exists(), "플러그인 파일은 지웠다")
        assertTrue(!java.io.File("build-tools/js-bundle/bundles.mjs").readText().contains("bootstrap-typeahead.js"), "번들 입력에서 빠져야 한다")
        assertTrue(!template("common/scripts.html").contains("typeahead"), "scripts.html은 플러그인을 불러오지 않는다")
        assertEquals(emptyList(), templateFiles().filter { it.readText().contains("data-provider=\"typeahead\"") }.map { it.path }, "BS2 자동 연결용 속성(동작하지도 않던 오타 data-provider)이 남은 템플릿")
        for (script in listOf("esm/service/project.Member.js", "esm/service/organization.Member.js", "esm/service/issue.LabelEditor.js", "esm/service/site.MassMail.js")) {
            val text = asset("javascripts/$script").readText()
            assertTrue(!text.contains("\$menu") && !text.contains("options.item") && !Regex("""\.typeahead\(""").containsMatchIn(text) && !text.contains("data(\"typeahead\")"), "$script 는 플러그인 내부에 기대지 않는다")
        }
        val bundles = listOf("yona-layout.js", "yona-common.js", "yona-lib.js").joinToString("\n") { asset("javascripts/$it").readText() }
        assertTrue(!bundles.contains("\$.fn.typeahead") && !bundles.contains("typeahead.data-api"), "번들에 플러그인 코드가 없어야 한다")
    }

    @Test
    fun `호출이 없던 yona_Sha1는 지웠고 템플릿도 부르지 않는다`() {
        // F-01: js-sha1 0.6.0 사본을 이슈 보기와 게시글 보기가 불러왔지만 어디서도 호출하지 않았다(본문 체크섬은 서버가 계산하고 v1.6도 같았다).
        assertTrue(!asset("javascripts/common/yona.Sha1.js").exists(), "yona.Sha1.js는 지웠다")
        assertEquals(emptyList(), templateFiles().filter { it.readText().contains("yona.Sha1") }.map { it.path }, "yona.Sha1.js를 부르는 템플릿")
    }

    @Test
    fun `2단 보기 패널은 jquery_pageslide 없이 Bootstrap 오프캔버스로 동작한다`() {
        // 시험용 구현(브랜치 trial/pageslide-offcanvas): jquery.pageslide 플러그인과 템플릿의 참조를 걷어내고 `yona.twoColumnMode.js`가 오프캔버스를 쓴다.
        assertTrue(!asset("javascripts/lib/jquery.pageslide.js").exists(), "플러그인 파일은 지웠다")
        assertEquals(emptyList(), templateFiles().filter { it.readText().contains("jquery.pageslide") }.map { it.path }, "플러그인을 부르는 템플릿")
        val script = asset("javascripts/esm/service/twoColumnMode.js").readText()
        assertTrue(script.contains("bootstrap.Offcanvas") && !script.contains(".pageslide(") && !script.contains("\$.pageslide"), "오프캔버스를 쓴다")
        assertTrue(java.io.File("app/assets/stylesheets/bootstrap.scss").readText().contains("vendor/bootstrap/offcanvas"), "Sass 구성에 오프캔버스가 들어 있어야 한다")
    }

    @Test
    fun `폼 전송은 jquery_form 없이 fetch로 보낸다`() {
        // $yona.sendForm이 숨은 폼과 jquery.form 3.09(ajaxForm) 대신 fetch로 같은 요청을 보낸다.
        assertTrue(!asset("javascripts/lib/jquery/jquery.form.js").exists(), "jquery.form은 지웠다")
        assertTrue(!java.io.File("build-tools/js-bundle/bundles.mjs").readText().contains("jquery.form"), "번들 입력에서 빠져야 한다")
        val common = asset("javascripts/common/yona.Common.js").readText()
        assertTrue(common.contains("function sendForm") && common.contains("fetch(sURL, htInit)") && !common.contains(".ajaxForm("), "sendForm은 fetch를 쓴다")
        val bundles = listOf("yona-layout.js", "yona-common.js", "yona-lib.js").joinToString("\n") { asset("javascripts/$it").readText() }
        assertTrue(!bundles.contains("ajaxSubmit") && !bundles.contains("jQuery Form Plugin"), "번들에 jquery.form 코드가 없어야 한다")
    }

    @Test
    fun `이미지 확대 보기는 Viewer_js 1_15_2이고 Yona의 renderList 패치는 들어 있지 않다`() {
        // 공식 1.15.2에서는 v1.6의 패치 대상 코드(renderList의 `outerWidth * i`)가 다시 쓰여 없어졌다. 시험 viewer.test.mjs가 같은 동작을 지킨다.
        val js = asset("javascripts/lib/viewerjs/viewer.js").readText()
        assertTrue(js.take(200).contains("Viewer.js v1.15.2"), "Viewer.js 1.15.2 파일이 있어야 한다")
        assertTrue(!js.contains("outerWidth * i"), "옛 renderList 코드가 남아 있으면 안 된다")
        assertTrue(asset("javascripts/lib/viewerjs/LICENSE").isFile, "라이선스 파일이 있어야 한다")
    }

    @Test
    fun `마크다운 변환은 공식 marked 4_3_0과 Yona 확장이고 서버와 브라우저가 같은 파일을 쓴다`() {
        // D-118: v1.6은 marked 1.2.9 파일을 직접 고쳐 썼다(패치 5곳). 공식 4.3.0을 그대로 두고 패치를 marked-yona.js로 옮겼다.
        val lib = asset("javascripts/lib/marked.js").readText()
        assertTrue(lib.take(200).contains("marked v4.3.0"), "marked 4.3.0 파일이어야 한다")
        assertTrue(!lib.contains("yb-header-"), "공식 파일에 Yona의 패치가 들어가면 안 된다")
        val server = java.io.File("src/main/resources/markdown")
        assertEquals(lib, java.io.File(server, "marked.js").readText(), "서버와 브라우저의 marked.js는 같아야 한다")
        assertEquals(asset("javascripts/lib/marked-yona.js").readText(), java.io.File(server, "marked-yona.js").readText(), "서버와 브라우저의 marked-yona.js는 같아야 한다")
        assertTrue(asset("javascripts/lib/marked-yona.js").readText().contains("yb-header-"))
    }

    @Test
    fun `코드 강조는 highlight_js 11_12_0이고 옛 API 호출이 남아 있지 않다`() {
        // D-119: 9.11 팩을 공식 cdn-assets 11.12.0에 v1.6의 언어를 이어 붙인 팩으로 바꿨다(build-tools/highlight-pack/build.sh).
        assertTrue(asset("javascripts/lib/highlight/highlight.pack.js").readText().take(300).contains("Highlight.js v11.12.0"))
        val markdown = asset("javascripts/common/yona.Markdown.js").readText()
        assertTrue(markdown.contains("hljs.highlightElement(") && !markdown.contains("highlightBlock("), "11의 API를 써야 한다")
        assertTrue(!markdown.contains("hljs.highlight(sLang"), "옛 두 인자 형태를 쓰면 안 된다")
        val template = File("src/main/resources/templates/common/markdown.html").readText()
        assertTrue(template.contains("hljs.highlightAll()") && !template.contains("initHighlightingOnLoad"))
    }

    @Test
    fun `이전 화면은 AngularJS가 아니라 Vue 3이고 vendor_js는 없다`() {
        // D-120: v1.6의 AngularJS 1.5.8 앱(vendor.js 1.6MB, 지원 종료)을 Vue 3 앱 한 개로 다시 썼다.
        assertTrue(!asset("javascripts/lib/vendor.js").exists(), "AngularJS와 lodash 묶음 vendor.js는 지웠다")
        assertTrue(asset("javascripts/lib/vue/vue.global.prod.js").readText().take(200).contains("vue v3.5.45"))
        val script = asset("javascripts/esm/service/Migration.js").readText()
        assertTrue(script.contains("Vue.createApp") && !script.contains("require('angular')") && !script.contains("angular.module"))
        val template = File("src/main/resources/templates/migration/home.html").readText()
        assertTrue(!Regex("\\sng-[a-z]+=").containsMatchIn(template), "템플릿에 AngularJS 속성이 남아 있다")
    }

    @Test
    fun `호출이 없던 diff_js와 jquery_browser_js와 magnific-popup_css는 없다`() {
        // D-122 1단계: 불러오기만 하고 부르는 코드가 없던 세 파일을 지웠다(JsDiff, $.browser, .mfp 클래스를 쓰는 곳이 없었다).
        listOf("javascripts/lib/diff.js", "javascripts/lib/jquery/jquery.browser.js", "javascripts/lib/magnific-popup/magnific-popup.css").forEach { assertTrue(!asset(it).exists(), it) }
        val templates = File("src/main/resources/templates").walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
        assertTrue(!templates.contains("lib/diff.js") && !templates.contains("jquery.browser.js") && !templates.contains("magnific-popup.css"))
    }

    @Test
    fun `humanize와 rgbcolor와 parseuri와 jquery_elevator는 표준 API와 작은 직접 구현으로 바뀌었다`() {
        // D-122 2단계: 파일 크기 글자, 색 읽기는 yona.Common.js, 주소 쿼리는 정규식, 엘리베이터는 yona.ui.Elevator.js
        listOf("javascripts/lib/humanize.js", "javascripts/lib/rgbcolor.js", "javascripts/lib/parseuri.js", "javascripts/lib/elevator/jquery.elevator.js").forEach { assertTrue(!asset(it).exists(), it) }
        assertTrue(asset("javascripts/lib/elevator/jquery.elevator.css").isFile, "스타일은 그대로 쓴다")
        val common = asset("javascripts/common/yona.Common.js").readText()
        assertTrue(common.contains("function formatFileSize") && common.contains("function getHexColor") && !common.contains("new RGBColor"))
        assertTrue(asset("javascripts/esm/ui/Elevator.js").isFile)
        val scripts = File("src/main/resources/static/assets/javascripts").walkTopDown().filter { it.isFile && it.extension == "js" && "/lib/" !in it.path && !it.name.startsWith("yona-") }.joinToString("\n") { it.readLines().filter { line -> !Regex("^\\s*(\\*|//|/\\*)").containsMatchIn(line) }.joinToString("\n") }
        assertTrue(!Regex("humanize\\.|new RGBColor|parseUri\\(|\\$\\.elevator").containsMatchIn(scripts), "옛 호출이 남아 있다")
    }

    @Test
    fun `favico와 validate_js는 작은 직접 구현으로 바뀌었다`() {
        // D-122 3단계: Favico.js(유지 중단)는 esm/ui/Favicon.js, validate.js(FormValidator)는 esm/ui/FormValidator.js(ES 모듈, 진입점 esm/index.js가 불러온다)
        listOf("javascripts/lib/favico/favico.min.js", "javascripts/lib/validate.js").forEach { assertTrue(!asset(it).exists(), it) }
        assertTrue(asset("javascripts/esm/ui/Favicon.js").isFile && asset("javascripts/esm/ui/FormValidator.js").isFile)
        val templates = File("src/main/resources/templates").walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
        assertTrue(!templates.contains("favico.min.js") && !templates.contains("lib/validate.js"))
        val scripts = File("src/main/resources/static/assets/javascripts/service").listFiles()!!.joinToString("\n") { f -> f.readLines().filter { !Regex("^\\s*(\\*|//|/\\*)").containsMatchIn(it) }.joinToString("\n") }
        assertTrue(!Regex("new Favico\\(|new FormValidator\\(").containsMatchIn(scripts), "옛 호출이 남아 있다")
    }

    @Test
    fun `ES 모듈 뼈대는 import map의 yona 접두어와 index_js 진입점으로 이어진다`() {
        // D-122: 모듈을 하나씩 옮겨 가는 길. esm/namespace.js가 옛 전역 이름 공간에 등록하는 다리이다
        val importmap = File("src/main/resources/templates/common/importmap.html").readText()
        assertTrue(importmap.contains("\"yona/\": \"[[@{/assets/javascripts/esm/}]]\""), "import map에 yona/ 접두어가 있어야 한다")
        val scripts = File("src/main/resources/templates/common/scripts.html").readText()
        assertTrue(scripts.contains("""<script type="module" th:src="@{/assets/javascripts/esm/index.js}"></script>"""))
        listOf("javascripts/esm/index.js", "javascripts/esm/namespace.js").forEach { assertTrue(asset(it).isFile, it) }
        assertTrue(asset("javascripts/esm/index.js").readText().contains("""import { register, ensureNamespace } from "yona/namespace.js";"""))
    }

    @Test
    fun `옮긴 ES 모듈은 진입점이 불러오고 템플릿에 옛 클래식 태그가 남지 않는다`() {
        // D-122: Elevator, Favicon, FormValidator를 esm/ui/로 옮겼다. 모듈은 common/scripts.html의 <script type="module" src="esm/index.js"> 하나로 모든 화면에 내려가므로 개별 태그가 없어야 한다
        val index = asset("javascripts/esm/index.js").readText()
        listOf("./ui/Elevator.js", "./ui/Favicon.js", "./ui/FormValidator.js").forEach { assertTrue(index.contains("import \"$it\";"), it) }
        listOf("common/yona.ui.Elevator.js", "common/yona.ui.Favicon.js", "common/yona.ui.FormValidator.js").forEach { assertTrue(!asset("javascripts/$it").exists(), it) }
        val templates = File("src/main/resources/templates").walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
        assertTrue(!Regex("yona\\.ui\\.(Elevator|Favicon|FormValidator)\\.js").containsMatchIn(templates), "템플릿에 옛 클래식 태그가 남아 있다")
        assertTrue(!File("build-tools/js-bundle/bundles.mjs").readText().contains("yona.ui.FormValidator.js"))
        listOf("javascripts/esm/ui/Elevator.js", "javascripts/esm/ui/Favicon.js", "javascripts/esm/ui/FormValidator.js").forEach { assertTrue(asset(it).readText().contains("register(\"yona.ui.")) }
    }
}
