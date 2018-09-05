package models.support;

import com.avaje.ebean.Expr;
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Junction;
import controllers.AbstractPostingApp;
import models.*;

import java.util.*;
import models.enumeration.IssueFilterType;

public class IssueSearchCondition  extends AbstractPostingApp.SearchCondition {
    public Long authorId;
    public Long assigneeId;
    public Long mentionId;
    public Long favoriteId;

    public ExpressionList<Issue> getExpressionListByFilter(IssueFilterType filter, User user) {
        if (filter.equals(IssueFilterType.ALL)) {
            this.assigneeId = user.id;
            this.authorId = user.id;
            this.mentionId = user.id;
            this.favoriteId = user.id;

            return asExpressionListForAll();
        } else {
            switch (filter) {
                case ASSIGNED:
                    this.assigneeId = user.id;
                    break;
                case CREATED:
                    this.authorId = user.id;
                    break;
                case MENTIONED:
                    this.mentionId = user.id;
                    break;
                case FAVORITE:
                    this.favoriteId = user.id;
                    break;
            }

            return asExpressionList();
        }
    }

    private ExpressionList<Issue> asExpressionList() {
        ExpressionList<Issue> el = Issue.finder.where();

        setAssigneeIfExists(el);
        setAuthorIfExist(el);
        setMentionedIssuesIfExist(el);
        setFavoriteIssuesIfExist(el);

        el.orderBy("updatedDate desc");
        return el;
    }

    private ExpressionList<Issue> asExpressionListForAll() {
        ExpressionList<Issue> el = Issue.finder.where();
        List<Long> mentioningIssueIds = Mention.getMentioningIssueIds(mentionId);
        List<Long> favoriteIssueIdsids = getFavoriteIssueIds(favoriteId);

        Junction<Issue> junction = el.disjunction();
        junction.add(Expr.eq("authorId", authorId));
        junction.add(Expr.eq("assignee.user.id", assigneeId));
        junction.add(Expr.in("id", mentioningIssueIds));
        junction.add(Expr.in("id", favoriteIssueIdsids));
        junction.endJunction();

        el.orderBy("updatedDate desc");
        return el;
    }

    private void setAuthorIfExist(ExpressionList<Issue> el) {
        if (authorId != null) {
            el.eq("authorId", authorId);
        }
    }

    private void setAssigneeIfExists(ExpressionList<Issue> el) {
        if (assigneeId != null) {
            el.eq("assignee.user.id", assigneeId);
        }
    }

    private void setMentionedIssuesIfExist(ExpressionList<Issue> el) {
        if (mentionId != null) {
            List<Long> ids = Mention.getMentioningIssueIds(mentionId);
            updateElWhenIdsEmpty(el, ids);
        }
    }

    private void setFavoriteIssuesIfExist(ExpressionList<Issue> el) {
        if (favoriteId != null) {
            List<Long> ids = getFavoriteIssueIds(favoriteId);
            updateElWhenIdsEmpty(el, ids);
        }
    }

    private void updateElWhenIdsEmpty(ExpressionList<Issue> el, List<Long> ids) {
        if (ids.isEmpty()) {
            el.idEq(-1);
        } else {
            el.idIn(ids);
        }
    }

    private List<Long> getFavoriteIssueIds(Long userId) {
        Set<Long> ids = new HashSet<>();
        List<FavoriteIssue> favoriteIssues = FavoriteIssue.find.where()
                .eq("user.id", userId)
                .findList();
        for (FavoriteIssue favoriteIssue : favoriteIssues) {
            ids.add(favoriteIssue.issue.id);
        }

        return new ArrayList<>(ids);
    }
}
