package dev.yona.domain.project

import org.springframework.data.jpa.repository.JpaRepository

/** 프로젝트 저장소이다. */
interface ProjectRepository : JpaRepository<Project, Long>

/** 프로젝트 구성원 저장소이다. */
interface ProjectUserRepository : JpaRepository<ProjectUser, Long> {

    /** 사용자가 프로젝트에 속해 있는지 확인한다. 역할은 보지 않는다 (v1.6 `ProjectUser.isMember`). */
    fun existsByUserIdAndProjectId(userId: Long, projectId: Long): Boolean

    /** 사용자가 프로젝트에서 지정한 역할인지 확인한다 (v1.6 `ProjectUser.isManager`는 역할 MANAGER). */
    fun existsByUserIdAndProjectIdAndRoleId(userId: Long, projectId: Long, roleId: Long): Boolean
}

/** 조직 저장소이다. */
interface OrganizationRepository : JpaRepository<Organization, Long> {

    /** 이름으로 조직을 조회한다 (v1.6 `Organization.findByName`). */
    fun findByNameIgnoreCase(name: String): Organization?
}

/** 프로젝트 이전 요청 저장소이다. */
interface ProjectTransferRepository : JpaRepository<ProjectTransfer, Long>
