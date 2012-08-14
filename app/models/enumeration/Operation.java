package models.enumeration;

public enum Operation {
    READ("read"), WRITE("write"), EDIT("edit"), DELETE("delete");
    
    private String operation;
    
    Operation(String operation) {
        this.operation = operation;
    }
    
    public String operation() {
        return this.operation;
    }
    
    public static Operation getValue(String value) {
        for (Operation operation : Operation.values()) {
            if (operation.operation().equals(value)) {
                return operation;
            }
        }
        return Operation.READ;
    }
}
