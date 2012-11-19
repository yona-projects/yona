package models.enumeration;

public enum State {
    ALL("all"), OPEN("open"), CLOSED("closed");
    private String state;

    State(String state) {
        this.state = state;
    }

    public String state() {
        return this.state;
    }

    public static State getValue(String value) {
        for (State issueState : State.values()) {
            if (issueState.state().equals(value)) {
                return issueState;
            }
        }
        return State.OPEN;
    }
}


