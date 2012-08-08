package models.enumeration;

public enum StateType {
    ALL("all"), OPEN("open"), CLOSED("closed");
    private String stateType;

    StateType(String stateType) {
        this.stateType = stateType;
    }

    public String stateType() {
        return this.stateType;
    }

    public static StateType getValue(String value) {
        for (StateType issueStateType : StateType.values()) {
            if (issueStateType.stateType().equals(value)) {
                return issueStateType;
            }
        }
        return StateType.OPEN;
    }
}


