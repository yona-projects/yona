package dev.yona.web.api.docs

import io.swagger.v3.oas.models.tags.Tag

/**
 * OpenAPI 문서의 태그(영역) 이름이다(로드맵 X-31, `decisions.md` D-50). 컨트롤러의 `@Operation(tags = [...])`가 이 상수를 쓰고,
 * [ALL]이 문서 화면에 보이는 순서와 설명을 정한다. 동작마다 태그는 하나이다.
 */
object ApiTags {
    const val SYSTEM = "상태 확인"
    const val AUTH = "기기 인증"
    const val USER = "사용자"
    const val ORGANIZATION = "조직"
    const val PROJECT = "프로젝트"
    const val LABEL = "라벨"
    const val MILESTONE = "마일스톤"
    const val ISSUE = "이슈"
    const val ISSUE_COMMENT = "이슈 댓글"
    const val BOARD = "게시판"
    const val BOARD_COMMENT = "게시판 댓글"
    const val PULL_REQUEST = "풀 리퀘스트"
    const val PULL_REQUEST_REVIEW = "풀 리퀘스트 리뷰와 댓글"
    const val WEBHOOK = "웹훅"
    const val SEARCH = "검색"
    const val FORK = "포크"
    const val WIKI = "위키"
    const val ATTACHMENT = "첨부 파일"

    /** 문서 화면의 태그 순서와 설명이다. */
    val ALL: List<Tag> = listOf(
        Tag().name(SYSTEM).description("서버가 살아 있는지 확인한다."),
        Tag().name(AUTH).description("CLI 같은 프로그램이 사용자의 웹 승인을 받아 개인용 토큰을 받는다(RFC 8628 기기 인증). 두 동작 모두 토큰 없이 부른다."),
        Tag().name(USER).description("사용자 만들기, 상태 바꾸기, 내 이슈와 활동 통계. 사이트 관리자 전용 동작이 있다."),
        Tag().name(ORGANIZATION).description("조직과 구성원 관리. 범위 `read:org`, `write:org`, `admin:org`."),
        Tag().name(PROJECT).description("프로젝트 조회와 만들기, 구성원, 담당자 후보, 내보내기."),
        Tag().name(LABEL).description("프로젝트의 라벨 조회, 만들기, 고치기, 지우기."),
        Tag().name(MILESTONE).description("프로젝트의 마일스톤 조회, 만들기, 고치기, 지우기."),
        Tag().name(ISSUE).description("이슈 조회, 만들기, 수정, 삭제, 라벨, 담당자, 공유자, 가중치, 지켜보기, 가져오기."),
        Tag().name(ISSUE_COMMENT).description("이슈 댓글 조회, 달기, 고치기, 지우기."),
        Tag().name(BOARD).description("게시글 조회, 만들기, 수정, 삭제, 라벨, 지켜보는 사람."),
        Tag().name(BOARD_COMMENT).description("게시글 댓글 조회, 달기, 고치기, 지우기."),
        Tag().name(PULL_REQUEST).description("풀 리퀘스트 조회, 만들기, 수정, 병합, 닫기, 담당자, 리뷰어 요청, 라벨, 원본 브랜치."),
        Tag().name(PULL_REQUEST_REVIEW).description("풀 리퀘스트의 리뷰 판정, 줄 댓글, 스레드, 임시 리뷰."),
        Tag().name(WEBHOOK).description("프로젝트 웹훅 목록, 만들기, 지우기. 범위 `read:repo_hook`, `write:repo_hook`, `admin:repo_hook`."),
        Tag().name(SEARCH).description("사이트 전체, 조직, 프로젝트 안의 검색."),
        Tag().name(FORK).description("프로젝트를 사용자나 조직으로 포크한다."),
        Tag().name(WIKI).description("프로젝트 위키 페이지 목록, 검색, 조회, 만들기, 고치기와 이름 바꾸기, 지우기, 이력, diff와 비교. 위키는 프로젝트마다 있는 별도 git 저장소이다."),
        Tag().name(ATTACHMENT).description("첨부 파일 올리기. 올린 파일의 번호를 글과 댓글의 `temporaryUploadFiles`에 담아 보낸다.")
    )
}
