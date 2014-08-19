package models.enumeration;

/**
 * @author Keeun Baik
 */
public enum SearchType {

    AUTO("auto"), NA("not available"), USER("user"), PROJECT("project"), ISSUE("issue"), POST("post"),
    MILESTONE("milestone"), ISSUE_COMMENT("issue_comment"), POST_COMMENT("post_comment"), REVIEW("review");

    private String searchType;

    SearchType(String value) {
        this.searchType = value;
    }

    public String getType() {
        return this.searchType;
    }

    public static SearchType getValue(String value) {
        for (SearchType searchType : SearchType.values()) {
            if (searchType.searchType.equals(value)) {
                return searchType;
            }
        }
        return NA;
    }

}
