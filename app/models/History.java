package models;

import playRepository.Commit;

import java.util.*;

/**
 * 프로젝트 Overview 화면에서 보여줄 히스토리를 나타내는 클래스
 *
 * @author Keesun Baik
 */
public class History {

    private String who;

    /**
     * 사용자 페이지로 이동할 수 있는 링크에 사용할 URL
     */
    private String userPageUrl;
    private String userAvatarUrl;
    private Date when;
    private String where;
    private String what;
    private String how;

    /**
     * 특정 작업(이슈, 게시물, 커밋)을 구체적으로 조회할 수 있는 링크에 사용할 URL
     */
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

    public void setUserAvatarUrl(String userAvatarUrl){
    	this.userAvatarUrl = userAvatarUrl;
    }
    
    public String getUserAvatarUrl(){
    	return userAvatarUrl;
    }
    
    /**
     * {@code commits}, {@code issues}, {@code postings} 목록으로 {@link History} 목록을 만들어 반환한다.
     *
     * when: 프로젝트 Overview 화면에 보여줄 히스토리 목록을 생성할 때 사용한다.
     *
     * 커밋 목록으로 히스토리 목록을 만들 때는 커밋 author의 email에 해당하는 사용자가 있는지 확인한다.
     * 해당하는 사용자가 없는 경우에는 {@link #userPageUrl}을 설정하지 않는다.
     * 히스토리 목록을 만든다음 최근에 발생한 이벤트 순으로 정렬하여 목록을 반환한다.
     *
     * @param userName
     * @param project
     * @param commits
     * @param issues
     * @param postings
     * @return
     */
    public static List<History> makeHistory(String userName, Project project, List<Commit> commits, List<Issue> issues, List<Posting> postings) {
        List<History> histories = new ArrayList<>();
        if(commits != null) {
            for(Commit commit : commits) {
                History commitHistory = new History();
                String authorEmail = commit.getAuthorEmail();
                if(User.isEmailExist(authorEmail)) {
                    setUserPageUrl(commitHistory, User.findByEmail(authorEmail));
                } else {
                    commitHistory.setWho(commit.getAuthorName());
                }
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
            issueHistory.setHow("#" + issue.id + " " + issue.title);
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
        history.setUserAvatarUrl(user.avatarUrl);
    }
}
