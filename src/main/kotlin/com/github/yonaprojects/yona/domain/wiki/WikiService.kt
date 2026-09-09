package com.github.yonaprojects.yona.domain.wiki

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.vcs.Commit

// Forgejo 수준 프로젝트 위키(P3-42) 서비스. 위키 페이지는 DB 엔티티가 아니라 프로젝트별
// bare git 저장소(`<owner>/<project>.wiki.git`, 프로젝트의 실제 VCS 종류와 무관하게 항상
// git) 안의 마크다운 파일이다 — 이 인터페이스는 그 저장소를 읽고 쓰는 창구 하나로 통일한다.
interface WikiService {

    // 위키 저장소가 이미 만들어졌는지(첫 페이지가 한 번이라도 커밋됐는지).
    fun isInitialized(project: Project): Boolean

    // 위키 저장소가 없으면 빈 bare 저장소를 만든다(첫 페이지 저장 직전 호출).
    fun ensureRepository(project: Project)

    // HEAD의 모든 페이지(.md 파일)를 재귀적으로 나열한다 — 사이드바/검색의 데이터 소스.
    fun listPages(project: Project): List<WikiPageSummary>

    // 제목(경로, 슬래시로 하위 경로 표현 가능)으로 페이지 본문을 읽는다. rev를 지정하면 해당
    // 리비전 시점의 내용을 읽는다(히스토리 화면의 "이 시점 보기"에 사용 가능).
    fun getPage(project: Project, title: String, rev: String = "HEAD"): WikiPageContent?

    // 페이지 저장(생성/수정/이름변경을 한 커밋으로 통합). oldTitle이 null이면 신규 생성,
    // oldTitle != newTitle이면 이름변경까지 함께 반영한다. message가 비어 있으면
    // "Create <title>"/"Update <title>"/"Rename <old> to <new>" 형태의 기본 메시지를 만든다.
    // 반환값은 새로 생성된 커밋 id.
    fun savePage(
        project: Project,
        user: User,
        oldTitle: String?,
        newTitle: String,
        content: String,
        message: String?
    ): String

    // 페이지 삭제.
    fun deletePage(project: Project, user: User, title: String, message: String?): String

    // 페이지 파일의 git log(오래된 순 아님, 최신순) — 리비전 목록.
    fun history(project: Project, title: String, pageNum: Int = 0, pageSize: Int = 20): List<Commit>

    // 지정 리비전(커밋)이 그 페이지 파일에 반영한 unified diff 텍스트(커밋 vs 부모, PathFilter로
    // 그 파일만 스코프). 리비전 목록 화면에서 항목을 고르면 이 diff를 보여준다.
    fun diff(project: Project, title: String, commitId: String): String

    // 임의의 두 리비전 사이에서 그 페이지 파일이 어떻게 달라졌는지의 unified diff 텍스트
    // ("리비전 간 diff" — 히스토리 목록에서 두 리비전을 골라 비교).
    fun compare(project: Project, title: String, revA: String, revB: String): String

    // 페이지 제목(파일명) 검색 — 대소문자 무시 부분일치.
    fun search(project: Project, query: String): List<WikiPageSummary>

    // 페이지 제목 <-> 저장소 내 파일 경로 변환. 슬래시를 그대로 하위 디렉터리 구분자로 쓴다
    // (Forgejo 확인 결과 동일 — "Guides/Setup" 제목은 "Guides/Setup.md" 파일).
    fun titleToPath(title: String): String
    fun pathToTitle(path: String): String
}
