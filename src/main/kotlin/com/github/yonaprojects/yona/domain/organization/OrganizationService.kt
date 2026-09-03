package com.github.yonaprojects.yona.domain.organization

interface OrganizationService {
    fun findByName(name: String): Organization?
    fun createOrganization(organization: Organization): Organization
    fun isNameExist(name: String): Boolean

    /**
     * 조직 신규 생성
     */
    fun createOrganization(name: String, descr: String?, creatorId: Long): Organization

    /**
     * 조직 정보 및 설정 변경
     */
    fun updateOrganizationSettings(orgId: Long, name: String, descr: String?, updaterId: Long)

    /**
     * 조직 멤버 추가
     */
    fun addOrganizationMember(orgId: Long, userLoginId: String, roleId: Long, updaterId: Long)

    /**
     * 조직 멤버 삭제
     */
    fun removeOrganizationMember(orgId: Long, userId: Long, removerId: Long)

    /**
     * 조직 멤버 권한 변경
     */
    fun updateOrganizationMemberRole(orgId: Long, userId: Long, newRoleId: Long, updaterId: Long)

    /**
     * 조직 삭제
     */
    fun deleteOrganization(orgId: Long, deleterId: Long)
    /**
     * 조직 멤버 가입 신청
     */
    fun enroll(orgName: String, userId: Long)

    /**
     * 조직 멤버 가입 신청 취소
     */
    fun cancelEnroll(orgName: String, userId: Long)

    /**
     * yona OrganizationApp.java:287-311 leave()/validateForLeave() 대응 (조직 그룹, TASK-0244).
     * 조직 관리자(ORG_ADMIN)면 항상 탈퇴 가능(legacy `AccessControl.isAllowed(user, org, LEAVE)` ==
     * `OrganizationUser.isAdmin(org, user)`이므로 관리자는 이 가드를 통째로 우회한다 — 마지막 관리자가
     * 스스로 탈퇴해 관리자가 0명이 되는 것을 legacy도 막지 않는 실제 동작이다, 그대로 이식).
     * 관리자가 아니면 조직 전체의 관리자 수가 1명일 때(탈퇴하려는 사람과 무관하게) 거부한다
     * (legacy 원문 그대로 — 관리자가 정확히 1명이면 일반 멤버조차 탈퇴가 막히는 조건도 재현).
     * @throws IllegalStateException 탈퇴가 허용되지 않을 때, 메시지는 메시지 키(organization.member.atLeastOneAdmin)
     */
    fun leaveOrganization(orgId: Long, userId: Long)
}

