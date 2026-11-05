package dev.yona.markdown

/**
 * README 마크다운 안의 상대 경로 이미지와 링크를 저장소 안의 주소로 바꾼다.
 * v1.6 `utils.Markdown.replaceImageLinkPath`, `replaceContentsLinkToCodeBrowerPath`에 해당한다.
 *
 * `./경로` 또는 `/./경로` 꼴로 시작하고 `http:`, `https:`, `ftp:`, `file:`로 시작하지 않는 대상만 바꾼다.
 * 이미지는 `/소유자/이름/files/기본브랜치/경로`, 일반 링크는 `/소유자/이름/code/기본브랜치/경로`가 된다.
 *
 * @property owner 프로젝트 소유자
 * @property name 프로젝트 이름
 * @property defaultBranch 저장소의 기본 브랜치. `refs/heads/` 접두어는 떼고 쓴다
 * @property contextPath 서버를 하위 경로에 둘 때의 경로(`application.context`). 없으면 빈 문자열이다
 */
class RelativeLinkRewriter(
    private val owner: String,
    private val name: String,
    defaultBranch: String,
    contextPath: String
) {

    private val branch = defaultBranch.replace("refs/heads/", "")
    private val context = if (contextPath.isNotEmpty()) "/$contextPath" else ""

    /** 코드 브라우저에서 `.md` 파일을 볼 때 쓴다. 이미지 경로만 바꾼다 (v1.6 `replaceImageLinkPath`). */
    fun forCodeBrowser(source: String): String = IMAGE_LINK.replace(source) { match ->
        "![${match.groupValues[1]}]($context/$owner/$name/files/$branch/${match.groupValues[2]})"
    }

    /** 프로젝트 홈의 README에 쓴다. 이미지는 `files`, 일반 링크는 `code` 주소로 바꾼다 (v1.6 `replaceContentsLinkToCodeBrowerPath`). */
    fun forReadme(source: String): String {
        val imagesFixed = forCodeBrowser(source)
        return NORMAL_LINK.replace(imagesFixed) { match ->
            "${match.groupValues[1]}[${match.groupValues[2]}]($context/$owner/$name/code/$branch/${match.groupValues[3]})"
        }
    }

    private companion object {
        private const val NOT_ABSOLUTE = "/?(?!https:|http:|ftp:|file:)[.][/]"
        // 링크 대상은 최소 일치(`.*?`)로 읽는다. v1.6은 탐욕 일치라 한 줄에 상대 링크가 여럿이면 첫 링크만 바뀌었다(C087)
        val IMAGE_LINK = Regex("!\\[([^]]*)]\\($NOT_ABSOLUTE(.*?)\\)")
        // 앞 문자가 `!`가 아니어야 한다. v1.6은 문자 하나를 꼭 요구해 문서 맨 앞의 링크를 놓쳤으므로(R-14) 문서 시작도 허용한다
        val NORMAL_LINK = Regex("(^|[^!])\\[([^]]*)]\\($NOT_ABSOLUTE(.*?)\\)")
    }
}
