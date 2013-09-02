package utils;

/**
 * @author Keesun Baik
 */
public class PullRequestCommit {

    private String projectOwner;

    private String projectName;

    private Long pullRequestNumber;

    private String commitId;

    public PullRequestCommit(String url) {
        String[] parts = url.split("/");
        this.projectOwner = parts[3];
        this.projectName = parts[4];
        this.pullRequestNumber = Long.parseLong(parts[6]);
        this.commitId = parts[8];
    }

    public static boolean isValid(String url) {
        if(url == null || url.trim().isEmpty()) {
            return false;
        }
        return url.matches(".*\\/pullRequest\\/[0-9]*\\/commit\\/.*");
    }

    public String getProjectOwner() {
        return projectOwner;
    }

    public String getProjectName() {
        return projectName;
    }

    public Long getPullRequestNumber() {
        return pullRequestNumber;
    }

    public String getCommitId() {
        return commitId;
    }
}
