package dev.yona.web.code

/** 코드 브라우저의 브랜치 선택 상자 항목이다. [url]은 고르면 이동할 주소, [label]은 참조 이름(`refs/heads/master`)이다. */
data class BranchOption(val url: String, val label: String, val selected: Boolean)

/** 위쪽 이동 경로(breadcrumb)의 한 칸이다. */
data class Crumb(val href: String, val text: String)

/**
 * 폴더 목록의 한 줄이다.
 *
 * @property id `cb-경로이름` 형태의 요소 식별자
 * @property dataPath 코드 브라우저 JS가 쓰는 `data-path`(이 항목의 전체 경로)
 * @property href 항목으로 이동할 주소(폴더는 `#cb-…` 앵커가 붙는다)
 * @property fileClass 아이콘 클래스(`dynatree-ico-cf` 폴더, `dynatree-ico-c` 파일)
 * @property userHref 작성자 링크. 알 수 없으면 `javascript:void();`
 * @property commitUrl 마지막으로 바꾼 커밋의 주소. 모르면 `null`
 */
data class FolderEntry(
    val id: String, val name: String, val folder: Boolean, val dataPath: String, val href: String, val fileClass: String,
    val userHref: String, val avatarUrl: String?, val commitUrl: String?, val msg: String?, val ago: String
)

/** 한 폴더의 목록이다. [listPath]는 폴더 경로(루트는 빈 값)이고 폴더가 앞, 파일이 뒤에 온다. */
data class FolderView(val listPath: String, val entries: List<FolderEntry>)

/**
 * 파일 보기이다.
 *
 * @property revId 파일 정보의 커밋 번호(브랜치 머리 커밋)
 * @property revIdString 원본과 열기 주소에 쓰는 리비전(브랜치 이름)
 * @property lineEnding 줄바꿈 종류 이름(`DOS`, `UNIX`, `UNDEFINED`). 이진 파일이면 빈 값
 * @property commentCount 이 파일에 달린 코드 리뷰 스레드 수
 * @property mode 본문 표시 방식: `image`, `binary`, `tooBig`, `markdown`, `code`
 * @property git Git 프로젝트이면 `true`. Subversion이면 리비전 번호를 `Revision N`으로 보인다
 */
data class FileView(
    val fileName: String, val path: String, val revId: String, val rawUrl: String, val openUrl: String, val historyUrl: String, val commitUrl: String,
    val userHref: String, val avatarUrl: String?, val author: String, val ago: String, val commitMessage: String, val lineEnding: String,
    val commentCount: Long, val binary: Boolean, val mode: String, val size: Long, val mimeType: String, val data: String?, val renderedMarkdown: String?,
    val editUrl: String?, val maxFileSize: Long, val siteManager: Boolean, val git: Boolean = true
)

/** 화면에 순서대로 놓는 항목이다. 폴더이면 [folder], 파일이면 [file]이 있다. */
data class CodeItem(val folder: FolderView?, val file: FileView?)

/** 코드 브라우저 화면 전체의 값이다. */
data class CodePage(
    val showTabs: Boolean, val filesTabUrl: String, val commitsTabUrl: String, val branchesTabUrl: String?, val branches: List<BranchOption>,
    val rootUrl: String, val projectName: String, val crumbs: List<Crumb>, val downloadUrl: String?, val newFileUrl: String?,
    val items: List<CodeItem>, val initialPath: String, val metaInfoUrl: String, val basePathUrl: String
)
