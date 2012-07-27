package models.enumeration;

public enum IssueStateType {
    ALL("all"), OPEN("open"), CLOSED("closed");
    private String stateType;

    IssueStateType(String stateType) {
        this.stateType = stateType;
    }

    public String stateType() {
        return this.stateType;
    }

    public static IssueStateType getValue(String value) {
        for (IssueStateType issueStateType : IssueStateType.values()) {
            if (issueStateType.stateType().equals(value)) {
                return issueStateType;
            }
        }
        return IssueStateType.OPEN;
    }
}


