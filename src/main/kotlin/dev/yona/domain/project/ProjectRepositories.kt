package dev.yona.domain.project

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

/** 프로젝트 저장소이다. */
interface ProjectRepository : JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    /**
     * 프로젝트 행을 잠근다. 값은 바꾸지 않는 갱신문이라 모든 DB에서 커밋이나 롤백까지 같은 행을 갱신하는 다른 트랜잭션이 기다리며,
     * 번호를 매기는 순서를 맞추는 데 쓴다. `select ... for update`(JPA 비관적 잠금)는 Cubrid에서 행 잠금으로 이어지지 않아 쓰지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Project p set p.name = p.name where p.id = :id")
    fun lockById(id: Long): Int

    /** 라벨을 붙인 프로젝트의 수이다 (v1.6 `label.projects.size()`). */
    fun countByLabelsId(labelId: Long): Long

    /** 조직에 속한 프로젝트이다 (v1.6 `organization.projects`). */
    fun findByOrganizationId(organizationId: Long): List<Project>

    /** 이 프로젝트를 원본으로 둔 포크를 조회한다 (v1.6 `Project.forkingProjects`). */
    fun findByOriginalProjectId(originalProjectId: Long): List<Project>


    /** 소유자(대소문자 구별)가 [originalProjectId]를 포크한 프로젝트이다 (v1.6 `Project.findByOwnerAndOriginalProject`, `eq`).*/
    fun findByOwnerAndOriginalProjectId(owner: String, originalProjectId: Long): List<Project>

    /** 소유자가 만든 프로젝트 중 공개 범위가 [scope]인 것을 만든 날짜 내림차순으로 조회한다 (v1.6 `Project.findProjectsCreatedByUserAndScope`). */
    /** 소유자(사용자나 조직)의 모든 프로젝트를 최근에 만든 순서로 읽는다. 읽을 수 있는지는 호출하는 쪽이 판단한다. */
    fun findByOwnerIgnoreCaseOrderByCreatedDateDesc(owner: String): List<Project>

    fun findByOwnerIgnoreCaseAndProjectScopeOrderByCreatedDateDesc(owner: String, scope: dev.yona.domain.enumeration.ProjectScope): List<Project>

    /** 같은 소유자에 같은 이름의 프로젝트가 있는지 확인한다 (v1.6 `Project.exists`, `ieq` 두 번). */
    fun existsByOwnerIgnoreCaseAndNameIgnoreCase(owner: String, name: String): Boolean

    /** 소유자와 이름이 모두 일치하는 프로젝트를 대소문자 구별 없이 조회한다 (v1.6의 `ieq`). */
    fun findByOwnerIgnoreCaseAndNameIgnoreCase(owner: String, name: String): Project?

    /** 지정한 이전 소유자와 이전 이름이 남아 있는 프로젝트를 최근에 바뀐 순으로 조회한다. */
    fun findByPreviousOwnerLoginIdIgnoreCaseAndPreviousNameIgnoreCaseOrderByPreviousNameChangedTimeDesc(
        previousOwnerLoginId: String, previousName: String
    ): List<Project>

    /** 사용자가 구성원으로 등록된 프로젝트이다 (v1.6 `Project.findProjectsByMember`). 같은 프로젝트가 여러 번 나올 수 있다. */
    @org.springframework.data.jpa.repository.Query("select pu.project from ProjectUser pu where pu.user.id = :userId order by pu.id")
    fun findProjectsByMember(userId: Long): List<Project>
}

/** 프로젝트 구성원 저장소이다. */
interface ProjectUserRepository : JpaRepository<ProjectUser, Long> {

    /** 사용자가 프로젝트에 속해 있는지 확인한다. 역할은 보지 않는다 (v1.6 `ProjectUser.isMember`). */
    fun existsByUserIdAndProjectId(userId: Long, projectId: Long): Boolean

    /** 사용자가 프로젝트에서 지정한 역할인지 확인한다 (v1.6 `ProjectUser.isManager`는 역할 MANAGER). */
    fun existsByUserIdAndProjectIdAndRoleId(userId: Long, projectId: Long, roleId: Long): Boolean

    /** 사용자가 프로젝트에서 가진 역할(매니저 또는 멤버) 행을 조회한다 (v1.6 `Role.findRoleByIds`). */
    fun findFirstByUserIdAndProjectId(userId: Long, projectId: Long): ProjectUser?

    /**
     * 사용자의 프로젝트 소속 행 중 사이트 관리자 역할이 아닌 것을 찾는다 (v1.6 `ProjectUser.findByIds`).
     * 사이트 관리자는 모든 프로젝트에 별도 행이 있으므로 구성원 처리에서는 제외한다.
     */
    fun findFirstByUserIdAndProjectIdAndRoleIdNot(userId: Long, projectId: Long, excludedRoleId: Long): ProjectUser?

    /** 프로젝트의 모든 소속 행을 역할과 상관없이 조회한다. */
    @org.springframework.data.jpa.repository.Query("select pu from ProjectUser pu join fetch pu.user where pu.project.id = :projectId")
    fun findMembersIncludingSiteManager(projectId: Long): List<ProjectUser>

    /** 프로젝트 구성원 행을 사이트 관리자 역할을 빼고 사용자 이름 오름차순으로 조회한다 (v1.6 `ProjectUser.findMemberListByProject`). */
    @org.springframework.data.jpa.repository.Query(
        "select pu from ProjectUser pu join fetch pu.user u join fetch pu.role r " +
            "where pu.project.id = :projectId and r.id <> :excludedRoleId order by u.name asc"
    )
    fun findMembers(projectId: Long, excludedRoleId: Long): List<ProjectUser>
}

/** 조직 저장소이다. */
interface OrganizationRepository : JpaRepository<Organization, Long> {

    /** 이름으로 조직을 조회한다 (v1.6 `Organization.findByName`). */
    fun findByNameIgnoreCase(name: String): Organization?

    /** 같은 이름의 조직이 있는지 확인한다 (v1.6 `Organization.isNameExist`). */
    fun existsByNameIgnoreCase(name: String): Boolean

    /**
     * 이름이나 설명에 [name]이 들어 있는 조직을 최신순으로 쪽 단위로 조회한다 (v1.6 `Organization.findByNameLike`). v1.6처럼 `_`와 `%`는 이스케이프하지 않는
     * `like` 와일드카드로 동작한다.
     * v1.6의 운영 DB(MariaDB)는 `like`가 대소문자를 구별하지 않으므로 같게 대소문자를 무시한다.
     */
    @org.springframework.data.jpa.repository.Query(
        value = "select o from Organization o where lower(o.name) like lower(concat('%', :#{T(dev.yona.util.LikeEscape).escape(#name)}, '%')) escape '\\' or lower(o.descr) like lower(concat('%', :#{T(dev.yona.util.LikeEscape).escape(#descr)}, '%')) escape '\\' order by o.id desc",
        countQuery = "select count(o) from Organization o where lower(o.name) like lower(concat('%', :#{T(dev.yona.util.LikeEscape).escape(#name)}, '%')) escape '\\' or lower(o.descr) like lower(concat('%', :#{T(dev.yona.util.LikeEscape).escape(#descr)}, '%')) escape '\\'"
    )
    fun findByNameContainingIgnoreCaseOrDescrContainingIgnoreCaseOrderByIdDesc(
        @org.springframework.data.repository.query.Param("name") name: String,
        @org.springframework.data.repository.query.Param("descr") descr: String, pageable: org.springframework.data.domain.Pageable
    ): org.springframework.data.domain.Page<Organization>
}

/** 프로젝트 이전 요청 저장소이다. */
interface ProjectTransferRepository : JpaRepository<ProjectTransfer, Long> {

    /** 같은 프로젝트, 보낸 사람, 받는 곳의 이전 요청을 찾는다 (v1.6 `ProjectTransfer.deleteExisting`, `requestNewTransfer`). */
    fun findByProjectIdAndSenderIdAndDestination(projectId: Long, senderId: Long, destination: String): List<ProjectTransfer>
}

/** 프로젝트 메뉴 설정 저장소이다. */
interface ProjectMenuSettingRepository : JpaRepository<ProjectMenuSetting, Long> {

    /** 프로젝트의 메뉴 설정을 조회한다. 프로젝트마다 하나가 있다. */
    fun findFirstByProjectId(projectId: Long): ProjectMenuSetting?
}

/** 최근 방문한 프로젝트 저장소이다. */
interface RecentProjectRepository : JpaRepository<RecentProject, Long> {

    /** 사용자의 방문 기록을 최근 순으로 조회한다 (v1.6 `RecentProject.getRecentProjects`). */
    fun findByUserIdOrderByIdDesc(userId: Long): List<RecentProject>

    /** 사용자가 같은 프로젝트를 방문한 기록을 조회한다. */
    fun findByUserIdAndProjectId(userId: Long, projectId: Long): List<RecentProject>

    /** 사용자의 방문 기록 개수이다. */
    fun countByUserId(userId: Long): Long

    /** 사용자의 최근 방문 기록을 모두 지운다 (v1.6 `RecentProject.deleteAll`). */
    fun deleteByUserId(userId: Long)
}

/** 제목 머리말 키워드 저장소이다. */
interface TitleHeadRepository : JpaRepository<TitleHead, Long> {

    /** 프로젝트의 키워드를 찾는다 (v1.6 `TitleHead.findByHeadKeyword`). */
    fun findFirstByProjectIdAndHeadKeyword(projectId: Long, headKeyword: String): TitleHead?

    /** 프로젝트에서 키워드에 [query]가 들어 있는(대소문자 무시) 것을 찾는다 (v1.6 `TitleHead.findByProject`). */
    @org.springframework.data.jpa.repository.Query(
        "select t from TitleHead t where t.project.id = :projectId and lower(t.headKeyword) like lower(concat('%', :#{T(dev.yona.util.LikeEscape).escape(#query)}, '%')) escape '\\' order by t.id"
    )
    fun search(projectId: Long, query: String): List<TitleHead>
}

/** 최근 푸시된 브랜치 저장소이다. */
interface PushedBranchRepository : JpaRepository<PushedBranch, Long> {

    /** 프로젝트에서 이름이 [name]인 푸시 기록이다 (v1.6 `PushedBranch.find.where().eq("project").eq("name")`). */
    fun findFirstByProjectIdAndName(projectId: Long, name: String): PushedBranch?

    /** 프로젝트에서 [since] 이후에 푸시한 기록이다 (v1.6 `Project.getRecentlyPushedBranches`). */
    fun findByProjectIdAndPushedDateAfter(projectId: Long, since: java.util.Date): List<PushedBranch>

    /** 프로젝트의 푸시 기록을 최근 순으로 조회한다. */
    fun findByProjectIdOrderByPushedDateDesc(projectId: Long): List<PushedBranch>
}
