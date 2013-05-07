package models;

import playRepository.Commit;

import java.util.*;

/**
 * @author Keesun Baik
 */
public class History {

    private String who;
    private String userPageUrl;
    private Date when;
    private String where;
    private String what;
    private String how;
    private String url;

    public String getWho() {
        return who;
    }

    public void setWho(String who) {
        this.who = who;
    }

    public Date getWhen() {
        return when;
    }

    public void setWhen(Date when) {
        this.when = when;
    }

    public String getWhere() {
        return where;
    }

    public void setWhere(String where) {
        this.where = where;
    }

    public String getWhat() {
        return what;
    }

    public void setWhat(String what) {
        this.what = what;
    }

    public String getHow() {
        return how;
    }

    public void setHow(String how) {
        this.how = how;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUserPageUrl() {
        return userPageUrl;
    }

    public void setUserPageUrl(String userPageUrl) {
        this.userPageUrl = userPageUrl;
    }

    public static List<History> makeHistory(String userName, Project project, List<Commit> commits, List<Issue> issues, List<Posting> postings) {
        List<History> histories = new ArrayList<History>();
        if(commits != null) {
            for(Commit commit : commits) {
                History commitHistory = new History();
                setUserPageUrl(commitHistory, User.findByEmail(commit.getAuthorEmail()));
                commitHistory.setWhen(commit.getCommitterDate());
                commitHistory.setWhere(project.name);
                commitHistory.setWhat("commit");
                commitHistory.setHow(commit.getShortId() + "-" + commit.getShortMessage());
                commitHistory.setUrl("/" + userName + "/" + project.name + "/commit/" + commit.getId());
                histories.add(commitHistory);
            }
        }

        for(Issue issue : issues) {
            History issueHistory = new History();
            String authorName = issue.authorName;
            issueHistory.setWho(authorName);
            setUserPageUrl(issueHistory, User.findByLoginId(issue.authorLoginId));
            issueHistory.setWhen(issue.createdDate);
            issueHistory.setWhere(project.name);
            issueHistory.setWhat("issue");
            issueHistory.setHow(issue.title);
            issueHistory.setUrl("/" + userName + "/" + project.name + "/issue/" + issue.id);
            histories.add(issueHistory);
        }

        for(Posting posting : postings) {
            History postingHistory = new History();
            String authorName = posting.authorName;
            postingHistory.setWho(authorName);
            setUserPageUrl(postingHistory, User.findByLoginId(posting.authorLoginId));
            postingHistory.setWhen(posting.createdDate);
            postingHistory.setWhere(project.name);
            postingHistory.setWhat("post");
            postingHistory.setHow(posting.title);
            postingHistory.setUrl("/" + userName + "/" + project.name + "/post/" + posting.id);
            histories.add(postingHistory);
        }

        Collections.sort(histories, new Comparator<History>() {
            @Override
            public int compare(History h1, History h2) {
                return h2.getWhen().compareTo(h1.getWhen());
            }
        });

        return histories;
    }

    private static void setUserPageUrl(History history, User user) {
        history.setWho(user.name);
        history.setUserPageUrl("/" + user.loginId);
    }
}
