package models.enumeration;

public enum IssueFilterType {
    ASSIGNED("assigned"),
    CREATED("created"),
    MENTIONED("mentioned"),
    FAVORITE("favorite"),
    ALL("all");

    private String issueFilter;

    IssueFilterType(String issueFilter) {
        this.issueFilter = issueFilter;
    }

    public static IssueFilterType getValue(String value) {
        for (IssueFilterType issueFilterType : IssueFilterType.values()) {
            if (issueFilterType.issueFilter.equals(value)) {
                return issueFilterType;
            }
        }
        throw new IllegalArgumentException("No matching issue filter type found for [" + value + "]");
    }
}
