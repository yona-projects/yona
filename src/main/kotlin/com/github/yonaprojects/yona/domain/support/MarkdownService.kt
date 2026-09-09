package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.project.Project

interface MarkdownService {
    fun render(body: String): String
    fun render(body: String, breaks: Boolean): String
    fun render(body: String, breaks: Boolean, project: Project?): String

    // yona Markdown.java render(source, project, breaks, lang)/render(source, project, lang)
    // 대응 — HTTP 요청 스레드가 없는 배치(예: 알림 다이제스트 메일 스케줄러)에서 렌더링할 때,
    // @멘션 표시 이름의 로케일을 LocaleContextHolder(요청 스레드 전용)가 아니라 호출자가 미리 알고 있는
    // 수신자의 언어로 명시적으로 지정할 수 있게 한다.
    fun render(body: String, breaks: Boolean, project: Project?, lang: String?): String

    // yona Markdown.java renderFileInCodeBrowser()/renderFileInReadme() 대응.
    // 렌더링 파이프라인은 render()와 동일하되, 렌더링 전에 소스 안의 상대경로 링크(`./path` 형태)를
    // 프로젝트 기본 브랜치 기준 절대경로(코드브라우저/파일 다운로드 경로)로 먼저 치환한다.
    fun renderFileInCodeBrowser(source: String, project: Project): String
    fun renderFileInReadme(source: String, project: Project): String

    // yona Markdown.java sanitize() 대응(common/partial_history.scala.html).
    // render()와 달리 commonmark 마크다운 파싱을 거치지 않고 이미 만들어진 HTML 문자열(예:
    // AbstractPostingApp.addToHistory()가 만든 <div class='history-made-by'>...</div> 조각)을
    // 그대로 새니타이즈 정책만 적용해 통과시킨다.
    fun sanitize(html: String): String
}
