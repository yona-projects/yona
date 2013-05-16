package utils;

public enum MenuType {
	SITE_HOME(1), NEW_PROJECT(2), PROJECTS(3), HELP(4), SITE_SETTING(5), USER(6),
    PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106), PULL_REQUEST(107), NONE(0);

    private int type;

    private MenuType(int type) {
        this.type = type;
    }
}
