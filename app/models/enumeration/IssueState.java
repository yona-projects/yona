package models.enumeration;

public enum IssueState {
    ENROLLED("enrolled"), ASSIGNED("assigned"), SOLVED("solved"), FINISHED("finished");
    private String state;

    IssueState(String state) {
        this.state = state;
    }

    public String state() {
        return this.state;
    }

    public static IssueState getValue(String value) {
        for (IssueState issueState : IssueState.values()) {
            if (issueState.state().equals(value)) {
                return issueState;
            }
        }
        return IssueState.ENROLLED;
    }
}
