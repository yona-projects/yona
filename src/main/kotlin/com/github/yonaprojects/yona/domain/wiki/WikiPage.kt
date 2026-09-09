package com.github.yonaprojects.yona.domain.wiki

import java.time.Instant

// 위키 페이지는 Forgejo와 동일하게 DB 엔티티가 아니라 프로젝트별 bare git 저장소
// (`<owner>/<project>.wiki.git`) 안의 마크다운 파일 그 자체다 — 아래 타입들은 그 저장소를
// 읽어 만든 값 객체일 뿐, JPA 엔티티가 아니다.

// 위키 페이지 목록(사이드바) 항목. path는 저장소 안 실제 파일 경로("Guides/Setup.md"),
// title은 그 경로에서 ".md" 확장자를 뗀 페이지 제목("Guides/Setup") — 슬래시가 그대로
// 남아있어 하위 경로/중첩 페이지 구조를 표현한다.
data class WikiPageSummary(
    val path: String,
    val title: String,
    val updatedAt: Instant?,
    val lastCommitMessage: String?,
    val lastAuthorName: String?
)

// 페이지 본문 조회 결과.
data class WikiPageContent(
    val path: String,
    val title: String,
    val content: String,
    // 이 내용을 읽어온 시점의 HEAD 커밋 id(동시편집 충돌감지에 사용 가능하도록 남겨둔다).
    val revision: String?
)
