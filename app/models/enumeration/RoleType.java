package models.enumeration;

public enum RoleType {
    MANAGER(1l), MEMBER(2l), SITEMANAGER(3l);

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
