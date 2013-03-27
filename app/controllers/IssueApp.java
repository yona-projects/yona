/**
 * @author Taehyun Park
 */

package controllers;

import models.*;
import models.enumeration.*;
import models.support.FinderTemplate;
import models.support.OrderParams;
import models.support.SearchParams;

import play.mvc.Http;
import views.html.issue.editIssue;
import views.html.issue.issue;
import views.html.issue.issueList;
import views.html.issue.newIssue;
import views.html.issue.notExistingPage;

import utils.AccessControl;
import utils.Callback;
import utils.JodaDateUtil;
import utils.HttpUtil;

import play.data.Form;
import play.mvc.Call;
import play.mvc.Result;

import jxl.write.WriteException;
import org.apache.tika.Tika;
import com.avaje.ebean.Page;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class IssueApp extends AbstractPostingApp {
    public static class SearchCondition extends AbstractPostingApp.SearchCondition {
        public String state;
        public Boolean commentedCheck;
        public Long milestoneId;
        public Set<Long> labelIds;
        public String authorLoginId;
        public Long assigneeId;

        public SearchCondition() {
            super();
            milestoneId = null;
            state = State.OPEN.name();
            commentedCheck = false;
        }
        public OrderParams getOrderParams() {
            return new OrderParams().add(orderBy, Direction.getValue(orderDir));
        }

        public SearchParams getSearchParam(Project project) {
            SearchParams searchParams = new SearchParams();

            searchParams.add("project.id", project.id, Matching.EQUALS);

            if (authorLoginId != null && !authorLoginId.isEmpty()) {
                User user = User.findByLoginId(authorLoginId);
                if (user != null) {
                    searchParams.add("authorId", user.id, Matching.EQUALS);
                } else {
                    List<Long> ids = new ArrayList<Long>();
                    for (User u : User.find.where().contains("loginId", authorLoginId).findList()) {
                        ids.add(u.id);
                    }
                    searchParams.add("authorId", ids, Matching.IN);
                }
            }

            if (assigneeId != null) {
                searchParams.add("assignee.user.id", assigneeId, Matching.EQUALS);
                searchParams.add("assignee.project.id", project.id, Matching.EQUALS);
            }

            if (filter != null && !filter.isEmpty()) {
                searchParams.add("title", filter, Matching.CONTAINS);
            }

            if (milestoneId != null) {
                searchParams.add("milestone.id", milestoneId, Matching.EQUALS);
            }

            if (labelIds != null) {
                for (Long labelId : labelIds) {
                    searchParams.add("labels.id", labelId, Matching.EQUALS);
                }
            }

            if (commentedCheck) {
                searchParams.add("numOfComments", AbstractPosting.NUMBER_OF_ONE_MORE_COMMENTS, Matching.GE);
            }

            State st = State.getValue(state);
            if (st.equals(State.OPEN) || st.equals(State.CLOSED)) {
                searchParams.add("state", st, Matching.EQUALS);
            }

            return searchParams;
        }
    }

    /**
     * 페이지 처리된 이슈들의 리스트를 보여준다.
     *
     * @param projectName
     *            프로젝트 이름
     * @param state
     *            이슈 해결 상태
     * @return
     * @throws IOException
     * @throws WriteException
     */
    public static Result issues(String userName, String projectName, String state, String format, int pageNum) throws WriteException, IOException {
        Project project = ProjectApp.getProject(userName, projectName);

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        Form<SearchCondition> issueParamForm = new Form<SearchCondition>(SearchCondition.class);
        SearchCondition issueParam = issueParamForm.bindFromRequest().get();
        issueParam.state = state;
        issueParam.pageNum = pageNum - 1;

        String[] labelIds = request().queryString().get("labelIds");
        if (labelIds != null) {
            for (String labelId : labelIds) {
                issueParam.labelIds.add(Long.valueOf(labelId));
            }
        }

        if (format.equals("xls")) {
            return issuesAsExcel(issueParam, issueParam.getOrderParams(), project, state);
        } else {
            Page<Issue> issues = FinderTemplate.getPage(
                issueParam.getOrderParams(), issueParam.getSearchParam(project),
                Issue.finder, ITEMS_PER_PAGE, issueParam.pageNum);
            return ok(issueList.render("title.issueList", issues, issueParam, project));
        }
    }

    public static Result issuesAsExcel(SearchCondition issueParam, OrderParams orderParams, Project project,
            String state) throws WriteException, IOException, UnsupportedEncodingException {
        List<Issue> issues = FinderTemplate.findBy(orderParams, issueParam.getSearchParam(project), Issue.finder);
        File excelFile = Issue.excelSave(issues, project.name + "_" + state + "_filter_"
                + issueParam.filter + "_milestone_" + issueParam.milestoneId);

        String filename = HttpUtil.encodeContentDisposition(excelFile.getName());

        response().setHeader("Content-Length", Long.toString(excelFile.length()));
        response().setHeader("Content-Type", new Tika().detect(excelFile));
        response().setHeader("Content-Disposition", "attachment; " + filename);

        return ok(excelFile);
    }

    public static Result issue(String userName, String projectName, Long issueId) {
        Project project = ProjectApp.getProject(userName, projectName);
        Issue issueInfo = Issue.finder.byId(issueId);

        if (!AccessControl.isCreatable(User.findByLoginId(session().get("loginId")), project, ResourceType.ISSUE_POST)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        if (issueInfo == null) {
            return ok(notExistingPage.render("title.post.notExistingPage", project));
        } else {
            for (IssueLabel label: issueInfo.labels) {
              label.refresh();
            }
            Form<Comment> commentForm = new Form<Comment>(Comment.class);
            Issue targetIssue = Issue.finder.byId(issueId);
            Form<Issue> editForm = new Form<Issue>(Issue.class).fill(targetIssue);
            return ok(issue.render("title.issueDetail", issueInfo, editForm, commentForm, project));
        }
    }

    public static Result newIssueForm(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        return newPostingForm(
                project, newIssue.render("title.newIssue", new Form<Issue>(Issue.class), project));
    }

    public static Result newIssue(String ownerName, String projectName) throws IOException {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();
        Project project = ProjectApp.getProject(ownerName, projectName);
        if (issueForm.hasErrors()) {
            return badRequest(newIssue.render(issueForm.errors().toString(), issueForm, project));
        } else {
            Issue newIssue = issueForm.get();
            newIssue.createdDate = JodaDateUtil.now();
            newIssue.setAuthor(UserApp.currentUser());
            newIssue.project = project;

            newIssue.state = State.OPEN;
            addLabels(newIssue.labels, request());

            newIssue.save();

            // Attach all of the files in the current user's temporary storage.
            Attachment.attachFiles(UserApp.currentUser().id, project.id, ResourceType.ISSUE_POST, newIssue.id);
        }

        return redirect(routes.IssueApp.issues(project.owner, project.name,
                State.OPEN.state(), "html", 1));
    }

    public static Result editIssueForm(String userName, String projectName, Long id) {
        Issue targetIssue = Issue.finder.byId(id);
        Form<Issue> editForm = new Form<Issue>(Issue.class).fill(targetIssue);
        Project project = ProjectApp.getProject(userName, projectName);
        if (!AccessControl.isAllowed(UserApp.currentUser(), targetIssue.asResource(), Operation.UPDATE)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        return ok(editIssue.render("title.editIssue", editForm, targetIssue, project));
    }

    public static void addLabels(Set<IssueLabel> labels, Http.Request request) {
        String[] labelIds = request.body().asMultipartFormData().asFormUrlEncoded()
                .get("labelIds");
        if (labelIds != null) {
            for (String labelId : labelIds) {
                labels.add(IssueLabel.findById(Long.parseLong(labelId)));
            }
        }
    };

    public static Result editIssue(String userName, String projectName, Long id) throws IOException {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();
        final Issue issue = issueForm.get();
        final Issue originalIssue = Issue.finder.byId(id);
        final Project project = originalIssue.project;
        Call redirectTo =
                routes.IssueApp.issues(project.owner, project.name, State.OPEN.name(), "html", 1);

        // updateIssueBeforeSave.run would be called just before this issue is saved.
        // It updates some properties only for issues, such as assignee or labels, but not for non-issues.
        Callback updateIssueBeforeSave = new Callback() {
            @Override
            public void run() {
                issue.project = project;
                addLabels(issue.labels, request());
            }
        };

        return editPosting(originalIssue, issue, issueForm, redirectTo, updateIssueBeforeSave);
    }

    public static Result deleteIssue(String userName, String projectName, Long issueId) {
        Issue issue = Issue.finder.byId(issueId);
        Project project = issue.project;

        return deletePosting(issue, routes.IssueApp.issues(project.owner, project.name,
                    State.OPEN.state(), "html", 1));
    }

    public static Result newComment(String userName, String projectName, Long issueId) throws IOException {
        final Issue issue = Issue.finder.byId(issueId);
        Project project = issue.project;
        Call redirectTo = routes.IssueApp.issue(project.owner, project.name, issueId);
        Form<IssueComment> commentForm = new Form<IssueComment>(IssueComment.class)
                .bindFromRequest();

        if (commentForm.hasErrors()) {
            return badRequest(commentForm.errors().toString());
        }

        final IssueComment comment = commentForm.get();

        return newComment(comment, commentForm, redirectTo, new Callback() {
            @Override
            public void run() {
                comment.issue = issue;
            }
        });
    }

    public static Result deleteComment(String userName, String projectName, Long issueId,
            Long commentId) {
        Comment comment = IssueComment.find.byId(commentId);
        Project project = comment.asResource().getProject();

        return deleteComment(comment,
                routes.IssueApp.issue(project.owner, project.name, comment.getParent().id));
    }
}
