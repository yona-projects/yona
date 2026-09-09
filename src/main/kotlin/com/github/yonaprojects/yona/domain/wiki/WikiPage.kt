package com.github.yonaprojects.yona.domain.wiki

import java.time.Instant

// 위키 페이지는 Forgejo와 동일하게 DB 엔티티가 아니라 프로젝트별 bare git 저장소
// (`<owner>/<project>.wiki.git`) 안의 마크다운 파일 그 자체다 — 아래 타입들은 그 저장소를
// 읽어 만든 값 객체일 뿐, JPA 엔티티가 아니다(P3-42).

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

// 위키 저장소 초기화 여부 및 저장소 정보를 표현. 별도 DB 필드 없이 매번 파일시스템을
// 확인한다(위키 페이지 수가 프로젝트당 수십~수백 건 수준이라 매 요청 disk stat 비용이
// 무시할 만하다고 판단 — 과도한 캐싱/DB 필드 추가로 설계를 부풀리지 않는다).
data class WikiPageRevision(
    val commitId: String,
    val shortMessage: String,
    val authorName: String?,
    val authorDate: Instant?
)
