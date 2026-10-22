package dev.yona.domain.project

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/** 프로젝트 저장소이다. */
interface ProjectRepository : JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    /** 소유자와 이름이 모두 일치하는 프로젝트를 대소문자 구별 없이 조회한다 (v1.6의 `ieq`). */
    fun findByOwnerIgnoreCaseAndNameIgnoreCase(owner: String, name: String): Project?

    /** 지정한 이전 소유자와 이전 이름이 남아 있는 프로젝트를 최근에 바뀐 순으로 조회한다. */
    fun findByPreviousOwnerLoginIdIgnoreCaseAndPreviousNameIgnoreCaseOrderByPreviousNameChangedTimeDesc(
        previousOwnerLoginId: String, previousName: String
    ): List<Project>
}

/** 프로젝트 구성원 저장소이다. */
interface ProjectUserRepository : JpaRepository<ProjectUser, Long> {

    /** 사용자가 프로젝트에 속해 있는지 확인한다. 역할은 보지 않는다 (v1.6 `ProjectUser.isMember`). */
    fun existsByUserIdAndProjectId(userId: Long, projectId: Long): Boolean

    /** 사용자가 프로젝트에서 지정한 역할인지 확인한다 (v1.6 `ProjectUser.isManager`는 역할 MANAGER). */
    fun existsByUserIdAndProjectIdAndRoleId(userId: Long, projectId: Long, roleId: Long): Boolean

    /** 사용자가 프로젝트에서 가진 역할(매니저 또는 멤버) 행을 조회한다 (v1.6 `Role.findRoleByIds`). */
    fun findFirstByUserIdAndProjectId(userId: Long, projectId: Long): ProjectUser?
}

/** 조직 저장소이다. */
interface OrganizationRepository : JpaRepository<Organization, Long> {

    /** 이름으로 조직을 조회한다 (v1.6 `Organization.findByName`). */
    fun findByNameIgnoreCase(name: String): Organization?
}

/** 프로젝트 이전 요청 저장소이다. */
interface ProjectTransferRepository : JpaRepository<ProjectTransfer, Long>

/** 프로젝트 메뉴 설정 저장소이다. */
interface ProjectMenuSettingRepository : JpaRepository<ProjectMenuSetting, Long> {

    /** 프로젝트의 메뉴 설정을 조회한다. 프로젝트마다 하나가 있다. */
    fun findFirstByProjectId(projectId: Long): ProjectMenuSetting?
}
