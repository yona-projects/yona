package models.enumeration;

public enum RoleType {
    MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);

    private Long roleType;

    RoleType(Long roleType) {
        this.roleType = roleType;
    }

    public Long roleType() {
        return this.roleType;
    }

    public String getLowerCasedName(){
        return this.name().toLowerCase();
    }
}
