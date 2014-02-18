package models.enumeration;

public enum Operation {
    READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), WATCH("watch"), LEAVE("leave");

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
