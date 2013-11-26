package playRepository;

import models.User;

/**
 * @author Keesun Baik
 */
public class GitBranch {

    private String name;

    private String shortName;

    private GitCommit headCommit;

    private User user;

    public GitBranch(String name, GitCommit headCommit) {
        this.name = name;
        this.shortName = name.replace("refs/heads/", "");
        this.headCommit = headCommit;
        this.user = User.findByCommitterEmail(headCommit.getCommitterEmail());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GitCommit getHeadCommit() {
        return headCommit;
    }

    public void setHeadCommit(GitCommit headCommit) {
        this.headCommit = headCommit;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }
}