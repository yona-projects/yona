package models.enumeration;

public enum RoleType {
    MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l);
    
    private Long roleType;
    
    RoleType(Long roleType) {
        this.roleType = roleType;
    }
    
    public Long roleType() {
        return this.roleType;
    }
}
