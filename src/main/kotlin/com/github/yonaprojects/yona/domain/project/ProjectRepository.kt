package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.domain.support.toSnakeCaseSort
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface ProjectRepository : JpaRepository<Project, Long> {
    fun findByOwnerAndName(owner: String, name: String): Optional<Project>
    fun existsByOwnerAndName(owner: String, name: String): Boolean
    fun findByOwner(owner: String): List<Project>
    fun countByLabelsId(labelId: Long): Long

    // issue/posting 번호 채번(project.lastIssueNumber/lastPostingNumber
    // 증가)이 전부 "읽고-증가시켜-저장"하는 read-modify-write 패턴인데, 그 사이 프로젝트 행에 아무
    // 잠금도 걸지 않아 동시 요청 두 개가 같은 값을 읽고 각각 저장하는 경쟁 상태에 노출돼 있었다
    // (unique 제약 위반으로 대부분 500).
    //
    // 처음엔 @Lock(PESSIMISTIC_WRITE)로 조회 후 증가시키는 방식을 시도했으나, H2(AUTO_SERVER=TRUE
    // 파일 모드)에서 "select ... for update"를 실제로는 블로킹하지 않음을 확인했다(MariaDB에서는
    // 정상 직렬화됨). SELECT FOR UPDATE의 블로킹 여부가 DB/모드마다 갈릴 수 있는 반면, UPDATE 문
    // 자체의 행 잠금은 모든 RDBMS가 예외 없이 갱신 시점에 즉시 배타 잠금을 거는 가장 기본적인 동작이라
    // 이 방식이 더 이식성이 높다 — "증가 UPDATE 실행 → 그 결과값을 다시 SELECT"로 원자적으로 채번한다.
    @Modifying
    @Query("UPDATE Project p SET p.lastIssueNumber = p.lastIssueNumber + 1 WHERE p.id = :id")
    fun incrementLastIssueNumber(@Param("id") id: Long): Int

    @Query("SELECT p.lastIssueNumber FROM Project p WHERE p.id = :id")
    fun findLastIssueNumber(@Param("id") id: Long): Long

    // 위와 동일한 근본원인/수정의 posting 버전(PostingServiceImpl.createPosting()).
    @Modifying
    @Query("UPDATE Project p SET p.lastPostingNumber = p.lastPostingNumber + 1 WHERE p.id = :id")
    fun incrementLastPostingNumber(@Param("id") id: Long): Int

    @Query("SELECT p.lastPostingNumber FROM Project p WHERE p.id = :id")
    fun findLastPostingNumber(@Param("id") id: Long): Long

    // legacy Project.findByOwnerAndOriginalProject(destination, project) 대응 —
    // 특정 소유자(destination) 밑에 이미 이 프로젝트를 원본으로 포크한 프로젝트가 있는지 조회.
    // git/fork.scala.html의 "이미 포크한 프로젝트가 있습니다" 안내 목록에 쓰인다.
    fun findByOwnerAndOriginalProject(owner: String, originalProject: Project): List<Project>

    // yona Project.projectNameChangeable(id, userName, projectName) 대응 — 대소문자 무시
    // 비교(`.ieq(...)`) + 본인(id) 제외(`.ne("id", id)`)로 같은 소유자 내 이름 중복 여부를 검사한다.
    fun existsByOwnerIgnoreCaseAndNameIgnoreCaseAndIdNot(owner: String, name: String, id: Long): Boolean

    // yona Project.findByPreviousPlaceOf(previousOwnerLoginId, previousName) 대응 —
    // 대소문자 무시 비교(yona `.ieq(...)`) + 가장 최근 변경 건 우선(`previousNameChangedTime desc`).
    fun findFirstByPreviousOwnerLoginIdIgnoreCaseAndPreviousNameIgnoreCaseOrderByPreviousNameChangedTimeDesc(
        previousOwnerLoginId: String,
        previousName: String
    ): Optional<Project>

    // yona Project.findByOwnerAndProjectName()의 "현재 이름으로 못 찾으면 예전 이름으로 재시도" 폴백
    // 대응. Kotlin 인터페이스 default 메서드로 둬 모든 호출부가 공용으로 재사용한다.
    fun findByOwnerAndNameOrPreviousPlace(owner: String, name: String): Optional<Project> {
        val direct = findByOwnerAndName(owner, name)
        if (direct.isPresent) {
            return direct
        }
        return findFirstByPreviousOwnerLoginIdIgnoreCaseAndPreviousNameIgnoreCaseOrderByPreviousNameChangedTimeDesc(
            owner, name
        )
    }

    @Query("""
        SELECT DISTINCT p.id FROM Project p 
        LEFT JOIN p.projectUsers pu 
        LEFT JOIN p.organization o 
        LEFT JOIN o.organizationUsers ou 
        WHERE p.projectScope = com.github.yonaprojects.yona.domain.project.ProjectScope.PUBLIC 
           OR (pu.user.id = :userId) 
           OR (ou.user.id = :userId AND p.projectScope = com.github.yonaprojects.yona.domain.project.ProjectScope.PROTECTED)
    """)
    fun findAllowedProjectIdsForUser(@Param("userId") userId: Long): List<Long>

    // yona Search.projectsEL()의 Application.HIDE_PROJECT_LISTING=true 분기 대응.
    // PUBLIC 프로젝트를 제외하고 "이 사용자가 직접 멤버이거나(모든 scope) 소속 조직이 PROTECTED로
    // 공개한" 프로젝트만 반환한다 — 사이트 전역으로 프로젝트 존재 자체를 숨기는 모드에서 쓰인다.
    @Query("""
        SELECT DISTINCT p.id FROM Project p 
        LEFT JOIN p.projectUsers pu 
        LEFT JOIN p.organization o 
        LEFT JOIN o.organizationUsers ou 
        WHERE (pu.user.id = :userId) 
           OR (ou.user.id = :userId AND p.projectScope = com.github.yonaprojects.yona.domain.project.ProjectScope.PROTECTED)
    """)
    fun findAllowedProjectIdsForUserExcludingPublic(@Param("userId") userId: Long): List<Long>

    @Query("""
        SELECT p.id FROM Project p 
        WHERE p.projectScope = com.github.yonaprojects.yona.domain.project.ProjectScope.PUBLIC
    """)
    fun findPublicProjectIds(): List<Long>

    // JPQL 대신 네이티브 쿼리를 쓰는 이유는 IssueRepository.searchIssues() 주석 참고 (Postgres
    // Hibernate 7.2.x LIKE 2개 이상 버그 회피 — name/overview 2개 컬럼이라 1개로 인수분해 불가).
    @Query(
        value = "SELECT * FROM project WHERE id IN :projectIds AND (LOWER(name) LIKE LOWER(:keyword) OR LOWER(overview) LIKE LOWER(:keyword))",
        countQuery = "SELECT COUNT(*) FROM project WHERE id IN :projectIds AND (LOWER(name) LIKE LOWER(:keyword) OR LOWER(overview) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun searchProjectsQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, pageable: Pageable): Page<Project>

    fun searchProjects(projectIds: List<Long>, keyword: String, pageable: Pageable): Page<Project> =
        searchProjectsQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM project WHERE id IN :projectIds AND (LOWER(name) LIKE LOWER(:keyword) OR LOWER(overview) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun countSearchProjectsQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String): Int

    fun countSearchProjects(projectIds: List<Long>, keyword: String): Int =
        countSearchProjectsQuery(projectIds.ifEmpty { listOf(-1L) }, keyword)

    @Query(
        value = "SELECT * FROM project WHERE (LOWER(name) LIKE LOWER(:query) OR LOWER(owner) LIKE LOWER(:query))",
        countQuery = "SELECT COUNT(*) FROM project WHERE (LOWER(name) LIKE LOWER(:query) OR LOWER(owner) LIKE LOWER(:query))",
        nativeQuery = true
    )
    fun findProjectsForAdminQuery(@Param("query") query: String, pageable: Pageable): Page<Project>

    fun findProjectsForAdmin(query: String, pageable: Pageable): Page<Project> =
        findProjectsForAdminQuery(query, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM project WHERE (LOWER(name) LIKE LOWER(:query) OR LOWER(owner) LIKE LOWER(:query))",
        nativeQuery = true
    )
    fun countProjectsForAdmin(@Param("query") query: String): Int
}
