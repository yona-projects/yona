package models.enumeration;

public enum IssueState {
    ENROLLED("issue.state.enrolled"), ASSIGNED("issue.state.assigned"), SOLVED("issue.state.solved"), FINISHED("issue.state.finished");
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
