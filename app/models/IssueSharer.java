/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

package models;

import models.enumeration.State;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.Date;

@Entity
public class IssueSharer extends Model {
    private static final long serialVersionUID = 6199025373911652405L;

    @Id
    public Long id;

    public Date created;

    public String loginId;

    @OneToOne
    public User user;

    @OneToOne
    public Issue issue;

    public static final String ADD = "add";
    public static final String DELETE = "delete";

    public static final Finder<Long, IssueSharer> find = new Finder<>(Long.class,
            IssueSharer.class);

    public static IssueSharer createSharer(String loginId, Issue issue) {
        IssueSharer issueSharer = new IssueSharer();
        issueSharer.loginId = loginId;
        issueSharer.created = new Date();
        issueSharer.issue = issue;
        issueSharer.user = User.findByLoginId(loginId);

        if (issueSharer.user == null) {
            String errorMsg = "Wrong loginId for issue sharing: " + loginId;
            play.Logger.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
        return issueSharer;
    }

    public static int getNumberOfIssuesSharedWithUser(Long userId){
        return find.where()
                .eq("user.id", userId)
                .eq("issue.state", State.OPEN)
                .findRowCount();
    }
}
