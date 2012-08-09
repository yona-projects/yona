package models.enumeration;

public enum PermissionOperation {
    READ("read"), WRITE("write");
    private String operation;
    
    PermissionOperation(String operation) {
        this.operation = operation;
    }
    
    public String operation() {
        return this.operation;
    }
    
    public static PermissionOperation getValue(String value) {
        for(PermissionOperation permissionOperation : PermissionOperation.values()) {
            if(permissionOperation.operation().equals(value)) {
                return permissionOperation;
            }
        }
        return PermissionOperation.READ;
    }
}
