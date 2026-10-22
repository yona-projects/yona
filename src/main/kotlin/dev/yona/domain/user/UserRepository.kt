package dev.yona.domain.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * 사용자 저장소이다. 조회 규칙은 v1.6 `User.findByLoginKey`와 같다.
 */
interface UserRepository : JpaRepository<User, Long> {

    /** 로그인 ID로 조회한다. v1.6의 `ieq`처럼 대소문자를 구별하지 않는다. */
    fun findByLoginIdIgnoreCase(loginId: String): User?

    /** 같은 로그인 ID의 사용자가 있는지 확인한다 (v1.6 `User.isLoginIdExist`, `ieq`). */
    fun existsByLoginIdIgnoreCase(loginId: String): Boolean

    /** 대표 이메일(`n4user.email`)로 정확히 일치하는 사용자를 조회한다. */
    fun findByEmail(email: String): User?

    /** 사용자가 프로젝트에 가입을 신청한 상태인지 확인한다 (v1.6 `User.enrolled(project)`). */
    @Query("select count(u) > 0 from User u join u.enrolledProjects p where u.id = :userId and p.id = :projectId")
    fun hasEnrolled(userId: Long, projectId: Long): Boolean

    /** 사용자가 조직에 가입을 신청한 상태인지 확인한다 (v1.6 `User.enrolled(organization)`). */
    @Query("select count(u) > 0 from User u join u.enrolledOrganizations o where u.id = :userId and o.id = :organizationId")
    fun hasEnrolledOrganization(userId: Long, organizationId: Long): Boolean

    /** 조직에 가입을 신청한 사용자이다 (v1.6 `organization.enrolledUsers`). */
    @Query("select u from User u join u.enrolledOrganizations o where o.id = :organizationId")
    fun findEnrolledUsersOfOrganization(organizationId: Long): List<User>

    /** 프로젝트에 가입을 신청한 사용자이다 (v1.6 `project.enrolledUsers`). */
    @Query("select u from User u join u.enrolledProjects p where p.id = :projectId")
    fun findEnrolledUsers(projectId: Long): List<User>

    /** 프로젝트에 가입을 신청한 사용자 수이다 (v1.6 `project.enrolledUsers.size`). */
    @Query("select count(u) from User u join u.enrolledProjects p where p.id = :projectId")
    fun countEnrolledUsers(projectId: Long): Long

    /**
     * 프로젝트의 구성원을 이름 순으로 조회한다. 사이트 관리자 행(모든 프로젝트에 자동으로 들어가는 `SITEMANAGER` 역할)은
     * 제외한다 (v1.6 `User.findUsersByProject`).
     */
    @Query(
        "select u from User u where u.id in (select pu.user.id from ProjectUser pu " +
            "where pu.project.id = :projectId and pu.role.id <> :excludedRoleId) order by u.name asc"
    )
    fun findMembersOfProject(projectId: Long, excludedRoleId: Long): List<User>

    /** 로그인 ID로 정확히 일치하는 사용자를 조회한다 (v1.6 `User.findByLoginId`는 `eq`). */
    fun findByLoginId(loginId: String): User?

    /** 개인 API 토큰(`n4user.token`)으로 사용자를 조회한다 (v1.6 `User.findByUserToken`). */
    fun findByToken(token: String): User?

    /**
     * 로그인 ID 또는 이메일로 사용자를 찾는다. 로그인 ID가 우선이고, 없을 때만 이메일로 찾는다
     * (v1.6 `User.findByLoginKey`).
     */
    fun findByLoginKey(loginIdOrEmail: String): User? =
        findByLoginIdIgnoreCase(loginIdOrEmail) ?: findByEmail(loginIdOrEmail)
}
