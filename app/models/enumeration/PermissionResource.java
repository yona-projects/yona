package models.enumeration;

public enum PermissionResource {
    BOARD("board"), ISSUE("issue"), MILESTONE("milestone"), CODE("code"), WIKI("wiki"), PROJECT("project"), SITE("site");
    private String resource;
    
    PermissionResource(String resource) {
        this.resource = resource;
    }
    
    public String resource() {
        return this.resource;
    }
    
    public static PermissionResource getValue(String value) {
        for(PermissionResource permissionResource : PermissionResource.values()) {
            if(permissionResource.resource().equals(value)) {
                return permissionResource;
            }
        }
        return PermissionResource.BOARD;
    }
}
