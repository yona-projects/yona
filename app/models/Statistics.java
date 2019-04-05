package models;

public class Statistics {
    public Integer issue;
    public Integer posting;
    public Integer assignedIssue;
    public Integer issueComment;
    public Integer postingComment;
    public Integer issueVoter;
    public Integer issueCommentVoter;

    private static final Statistics EMPTY = new Statistics();

    public static Statistics empty() {
        return EMPTY;
    }

    public Statistics() {
        this.issue = 0;
        this.posting = 0;
        this.assignedIssue = 0;
        this.issueComment = 0;
        this.postingComment = 0;
        this.issueVoter = 0;
        this.issueCommentVoter = 0;
    }
}
