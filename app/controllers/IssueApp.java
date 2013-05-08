package controllers;

import models.*;
import models.enumeration.*;

import play.mvc.Http;
import views.html.issue.editIssue;
import views.html.issue.issue;
import views.html.issue.issueList;
import views.html.issue.newIssue;
import views.html.issue.notExistingPage;
import views.html.project.unauthorized;

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
import com.avaje.ebean.ExpressionList;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.avaje.ebean.Expr.icontains;

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

        private ExpressionList<Issue> asExpressionList(Project project) {
            ExpressionList<Issue> el = Issue.finder.where().eq("project.id", project.id);

            if (filter != null) {
                el.or(icontains("title", filter), icontains("body", filter));
            }

            if (authorLoginId != null && !authorLoginId.isEmpty()) {
                User user = User.findByLoginId(authorLoginId);
                if (!user.isAnonymous()) {
                    el.eq("authorId", user.id);
                } else {
                    List<Long> ids = new ArrayList<Long>();
                    for (User u : User.find.where().icontains("loginId", authorLoginId).findList()) {
                        ids.add(u.id);
                    }
                    el.in("authorId", ids);
                }
            }

            if (assigneeId != null) {
                el.eq("assignee.user.id", assigneeId);
                el.eq("assignee.project.id", project.id);
            }

            if (milestoneId != null) {
                el.eq("milestone.id", milestoneId);
            }

            if (labelIds != null) {
                for (Long labelId : labelIds) {
                    el.eq("labels.id", labelId);
                }
            }

            if (commentedCheck) {
                el.ge("numOfComments", AbstractPosting.NUMBER_OF_ONE_MORE_COMMENTS);
            }

            State st = State.getValue(state);
            if (st.equals(State.OPEN) || st.equals(State.CLOSED)) {
                el.eq("state", st);
            }

            if (orderBy != null) {
                el.orderBy(orderBy + " " + orderDir);
            }

            return el;
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
            return forbidden(unauthorized.render(project));
        }

        Form<SearchCondition> issueParamForm = new Form<SearchCondition>(SearchCondition.class);
        SearchCondition searchCondition = issueParamForm.bindFromRequest().get();
        searchCondition.pageNum = pageNum - 1;
        searchCondition.state = state;

        String[] labelIds = request().queryString().get("labelIds");
        if (labelIds != null) {
            for (String labelId : labelIds) {
                searchCondition.labelIds.add(Long.valueOf(labelId));
            }
        }

        ExpressionList<Issue> el = searchCondition.asExpressionList(project);

        if (format.equals("xls")) {
            return issuesAsExcel(el, project);
        } else {
            Page<Issue> issues = el
                .findPagingList(ITEMS_PER_PAGE).getPage(searchCondition.pageNum);

            return ok(issueList.render("title.issueList", issues, searchCondition, project));
        }
    }

    public static Result issuesAsExcel(ExpressionList<Issue> el, Project project)
            throws WriteException, IOException, UnsupportedEncodingException {
        byte[] excelData = Issue.excelFrom(el.findList());
        String filename = HttpUtil.encodeContentDisposition(
                project.name + "_issues_" + JodaDateUtil.today().getTime() + ".xls");

        response().setHeader("Content-Type", new Tika().detect(filename));
        response().setHeader("Content-Disposition", "attachment; " + filename);

        return ok(excelData);
    }

    public static Result issue(String userName, String projectName, Long issueId) {
        Project project = ProjectApp.getProject(userName, projectName);
        Issue issueInfo = Issue.finder.byId(issueId);

        if (!AccessControl.isAllowed(UserApp.currentUser(), issueInfo.asResource(), Operation.READ)) {
            return forbidden(unauthorized.render(project));
        }

        if (issueInfo == null) {
            return notFound(notExistingPage.render("title.post.notExistingPage", project));
        }

        for (IssueLabel label: issueInfo.labels) {
            label.refresh();
        }

        Form<Comment> commentForm = new Form<Comment>(Comment.class);
        Form<Issue> editForm = new Form<Issue>(Issue.class).fill(Issue.finder.byId(issueId));

        return ok(issue.render("title.issueDetail", issueInfo, editForm, commentForm, project));
    }

    public static Result newIssueForm(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);

        return newPostingForm(project, ResourceType.ISSUE_POST,
                newIssue.render("title.newIssue", new Form<Issue>(Issue.class), project));
    }

    public static Result newIssue(String ownerName, String projectName) throws IOException {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();
        Project project = ProjectApp.getProject(ownerName, projectName);

        if (!AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, ResourceType.ISSUE_POST)) {
            return forbidden(unauthorized.render(project));
        }

        if (issueForm.hasErrors()) {
            return badRequest(newIssue.render(issueForm.errors().toString(), issueForm, project));
        }

        Issue newIssue = issueForm.get();
        newIssue.createdDate = JodaDateUtil.now();
        newIssue.setAuthor(UserApp.currentUser());
        newIssue.project = project;

        newIssue.state = State.OPEN;
        addLabels(newIssue.labels, request());

        setMilestone(issueForm, newIssue);

        newIssue.save();

        // Attach all of the files in the current user's temporary storage.
        Attachment.attachFiles(UserApp.currentUser().id, newIssue.asResource());

        return redirect(routes.IssueApp.issues(project.owner, project.name,
                State.OPEN.state(), "html", 1));
    }

    public static Result editIssueForm(String userName, String projectName, Long id) {
        Issue issue = Issue.finder.byId(id);
        Project project = ProjectApp.getProject(userName, projectName);

        if (!AccessControl.isAllowed(UserApp.currentUser(), issue.asResource(), Operation.UPDATE)) {
            return forbidden(unauthorized.render(project));
        }

        Form<Issue> editForm = new Form<Issue>(Issue.class).fill(issue);

        return ok(editIssue.render("title.editIssue", editForm, issue, project));
    }

    public static Result editIssue(String userName, String projectName, Long id) throws IOException {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();
        final Issue issue = issueForm.get();
        setMilestone(issueForm, issue);
        final Issue originalIssue = Issue.finder.byId(id);
        final Project project = originalIssue.project;
        Call redirectTo =
                routes.IssueApp.issues(project.owner, project.name, State.OPEN.name(), "html", 1);

        // updateIssueBeforeSave.run would be called just before this issue is saved.
        // It updates some properties only for issues, such as assignee or labels, but not for non-issues.
        Callback updateIssueBeforeSave = new Callback() {
            @Override
            public void run() {
                issue.comments = originalIssue.comments;
                addLabels(issue.labels, request());
            }
        };

        return editPosting(originalIssue, issue, issueForm, redirectTo, updateIssueBeforeSave);
    }

    private static void setMilestone(Form<Issue> issueForm, Issue issue) {
        String milestoneId = issueForm.data().get("milestoneId");
        if(milestoneId != null && !milestoneId.isEmpty()) {
            issue.milestone = Milestone.findById(Long.parseLong(milestoneId));
        }
    }

    public static Result deleteIssue(String userName, String projectName, Long issueId) {
        Issue issue = Issue.finder.byId(issueId);
        Project project = issue.project;
        Call redirectTo =
            routes.IssueApp.issues(project.owner, project.name, State.OPEN.state(), "html", 1);

        return delete(issue, issue.asResource(), redirectTo);
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
        Call redirectTo =
            routes.IssueApp.issue(project.owner, project.name, comment.getParent().id);

        return delete(comment, comment.asResource(), redirectTo);
    }

    public static void addLabels(Set<IssueLabel> labels, Http.Request request) {
        Http.MultipartFormData multipart = request.body().asMultipartFormData();
        Map<String, String[]> form;
        if (multipart != null) {
            form = multipart.asFormUrlEncoded();
        } else {
            form = request.body().asFormUrlEncoded();
        }
        String[] labelIds = form.get("labelIds");
        if (labelIds != null) {
            for (String labelId : labelIds) {
                labels.add(IssueLabel.findById(Long.parseLong(labelId)));
            }
        }
    }
}
