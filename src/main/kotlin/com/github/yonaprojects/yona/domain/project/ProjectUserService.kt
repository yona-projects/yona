package com.github.yonaprojects.yona.domain.project

interface ProjectUserService {
    fun getProjectMembers(projectId: Long): List<ProjectUser>
    
    /**
     * 프로젝트 가입 신청
     */
    fun enroll(projectId: Long, userId: Long)
    
    /**
     * 프로젝트 가입 신청 취소
     */
    fun cancelEnroll(projectId: Long, userId: Long)
    
    /**
     * 프로젝트 가입 신청 승인
     */
    fun acceptMemberRequest(projectId: Long, userId: Long, approverId: Long)
    
    /**
     * 프로젝트 가입 신청 거절
     */
    fun rejectMemberRequest(projectId: Long, userId: Long, rejecterId: Long)
    
    /**
     * 프로젝트 멤버 직접 추가
     */
    fun addMember(projectId: Long, loginId: String, updaterId: Long)
    
    /**
     * 프로젝트 멤버 권한 수정
     */
    fun updateMemberRole(projectId: Long, userId: Long, newRoleId: Long, updaterId: Long)
    
    /**
     * 프로젝트 멤버 삭제 (또는 탈퇴)
     */
    fun removeMember(projectId: Long, userId: Long, removerId: Long)
}
