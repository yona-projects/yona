package models.enumeration;

public enum Resource {
    ISSUE_POST("issue_post"),
    ISSUE_COMMENT("issue_comment"),
    ISSUE_ASSIGNEE("issue_assignee"),
    ISSUE_STATE("issue_state"),
    ISSUE_CATEGORY("issue_category"),
    ISSUE_MILESTONE("issue_milestone"),
    ISSUE_NOTICE("issue_notice"),
    ISSUE_LABEL("issue_label"),
    BOARD_POST("board_post"),
    BOARD_COMMENT("board_comment"),
    BOARD_CATEGORY("board_category"),
    BOARD_NOTICE("board_notice"),
    CODE("code"),
    MILESTONE("milestone"),
    WIKI_PAGE("wiki_page"),
    PROJECT_SETTING("project_setting"),
    SITE_SETTING("site_setting"),
    USER("user"),
    USER_AVATAR("user_avatar"),
    PROJECT("project");

    private String resource;

    Resource(String resource) {
        this.resource = resource;
    }

    public String resource() {
        return this.resource;
    }

    public static Resource getValue(String value) {
        for (Resource resource : Resource.values()) {
            if (resource.resource().equals(value)) {
                return resource;
            }
        }
        return Resource.ISSUE_POST;
    }
}