/**
 * @author Taehyun Park
 */

package controllers;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import play.Logger;

import jxl.write.WriteException;

import models.Assignee;
import models.Attachment;
import models.Issue;
import models.IssueComment;
import models.IssueLabel;
import models.Project;
import models.enumeration.Direction;
import models.enumeration.Operation;
import models.enumeration.Resource;
import models.enumeration.State;
import models.support.FinderTemplate;
import models.support.OrderParams;
import models.support.SearchCondition;

import org.apache.tika.Tika;

import play.cache.Cached;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;
import utils.Constants;
import utils.HttpUtil;
import utils.JodaDateUtil;
import views.html.issue.editIssue;
import views.html.issue.issue;
import views.html.issue.issueList;
import views.html.issue.newIssue;
import views.html.issue.notExistingPage;

import com.avaje.ebean.Page;

public class IssueApp extends Controller {

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
        OrderParams orderParams = new OrderParams().add(issueParam.sortBy,
                Direction.getValue(issueParam.orderBy));
        issueParam.state = state;
        issueParam.pageNum = pageNum - 1;

        String[] labelIds = request().queryString().get("labelIds");
        if (labelIds != null) {
            for (String labelId : labelIds) {
                issueParam.labelIds.add(Long.valueOf(labelId));
            }
        }

        if (format.equals("xls")) {
            return issuesAsExcel(issueParam, orderParams, project, state);
        } else {
            Page<Issue> issues = FinderTemplate.getPage(orderParams, issueParam.asSearchParam(project),
                Issue.finder, Issue.ISSUE_COUNT_PER_PAGE, issueParam.pageNum);
            return ok(issueList.render("title.issueList", issues, issueParam, project));
        }
    }

    public static Result issuesAsExcel(SearchCondition issueParam, OrderParams orderParams, Project project,
            String state) throws WriteException, IOException, UnsupportedEncodingException {
        List<Issue> issues = FinderTemplate.findBy(orderParams, issueParam.asSearchParam(project), Issue.finder);
        File excelFile = Issue.excelSave(issues, project.name + "_" + state + "_filter_"
                + issueParam.filter + "_milestone_" + issueParam.milestone);

        String filename = HttpUtil.encodeContentDisposition(excelFile.getName());

        response().setHeader("Content-Length", Long.toString(excelFile.length()));
        response().setHeader("Content-Type", new Tika().detect(excelFile));
        response().setHeader("Content-Disposition", "attachment; " + filename);

        return ok(excelFile);
    }

    public static Result issue(String userName, String projectName, Long issueId) {
        Project project = ProjectApp.getProject(userName, projectName);
        Issue issueInfo = Issue.findById(issueId);
        if (issueInfo == null) {
            return ok(notExistingPage.render("title.post.notExistingPage", project));
        } else {
            for (IssueLabel label: issueInfo.labels) {
              label.refresh();
            }
            Form<IssueComment> commentForm = new Form<IssueComment>(IssueComment.class);
            Issue targetIssue = Issue.findById(issueId);
            Form<Issue> editForm = new Form<Issue>(Issue.class).fill(targetIssue);
            return ok(issue.render("title.issueDetail", issueInfo, editForm, commentForm, project));
        }
    }

    public static Result newIssueForm(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        if (UserApp.currentUser() == UserApp.anonymous) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        return ok(newIssue.render("title.newIssue", new Form<Issue>(Issue.class), project));
    }

    public static Result newIssue(String ownerName, String projectName) throws IOException {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();
        Project project = ProjectApp.getProject(ownerName, projectName);
        if (issueForm.hasErrors()) {
            return badRequest(newIssue.render(issueForm.errors().toString(), issueForm, project));
        } else {
            Issue newIssue = issueForm.get();
            newIssue.date = JodaDateUtil.now();
            newIssue.authorId = UserApp.currentUser().id;
            newIssue.authorLoginId = UserApp.currentUser().loginId;
            newIssue.authorName = UserApp.currentUser().name;
            newIssue.project = project;
            newIssue.state = State.OPEN;
            if (newIssue.assignee.user.id != null) {
                newIssue.assignee = Assignee.add(newIssue.assignee.user.id, project.id);
            } else {
                newIssue.assignee = null;
            }
            String[] labelIds = request().body().asMultipartFormData().asFormUrlEncoded()
                    .get("labelIds");
            if (labelIds != null) {
                for (String labelId : labelIds) {
                    newIssue.labels.add(IssueLabel.findById(Long.parseLong(labelId)));
                }
            }

            Long issueId = Issue.create(newIssue);

            // Attach all of the files in the current user's temporary storage.
            Attachment.attachFiles(UserApp.currentUser().id, project.id, Resource.ISSUE_POST, issueId);
        }
        return redirect(routes.IssueApp.issues(project.owner, project.name,
                State.OPEN.state(), "html", 1));
    }

    public static Result editIssueForm(String userName, String projectName, Long id) {
        Issue targetIssue = Issue.findById(id);
        Form<Issue> editForm = new Form<Issue>(Issue.class).fill(targetIssue);
        Project project = ProjectApp.getProject(userName, projectName);
        if (!AccessControl.isAllowed(UserApp.currentUser(), targetIssue.asResource(), Operation.UPDATE)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        return ok(editIssue.render("title.editIssue", editForm, targetIssue, project));
    }

    public static Result editIssue(String userName, String projectName, Long id) throws IOException {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();

        if (issueForm.hasErrors()) {
            return badRequest(issueForm.errors().toString());
        }

        Issue issue = issueForm.get();
        Issue originalIssue = Issue.findById(id);

        issue.id = id;
        issue.date = originalIssue.date;
        issue.authorId = originalIssue.authorId;
        issue.authorLoginId = originalIssue.authorLoginId;
        issue.authorName = originalIssue.authorName;
        issue.project = originalIssue.project;
        if (issue.assignee.user.id != null) {
            issue.assignee = Assignee.add(issue.assignee.user.id, originalIssue.project.id);
        } else {
            issue.assignee = null;
        }
        String[] labelIds = request().body().asMultipartFormData().asFormUrlEncoded()
                .get("labelIds");
        if (labelIds != null) {
            for (String labelId : labelIds) {
                issue.labels.add(IssueLabel.findById(Long.parseLong(labelId)));
            }
        }

        Issue.edit(issue);

        // Attach the files in the current user's temporary storage.
        Attachment.attachFiles(UserApp.currentUser().id, originalIssue.project.id, Resource.ISSUE_POST, id);

        return redirect(routes.IssueApp.issues(originalIssue.project.owner, originalIssue.project.name, State.OPEN.name(), "html", 1));
    }

    public static Result deleteIssue(String userName, String projectName, Long issueId) {
        Project project = ProjectApp.getProject(userName, projectName);

        Issue.delete(issueId);
        Attachment.deleteAll(Resource.ISSUE_POST, issueId);
        return redirect(routes.IssueApp.issues(project.owner, project.name,
                State.OPEN.state(), "html", 1));
    }

    public static Result newComment(String userName, String projectName, Long issueId) throws IOException {
        Form<IssueComment> commentForm = new Form<IssueComment>(IssueComment.class)
                .bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);
        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "board.comment.empty");
            return redirect(routes.IssueApp.issue(project.owner, project.name, issueId));
        } else {
            IssueComment comment = commentForm.get();
            comment.issue = Issue.findById(issueId);
            comment.authorId = UserApp.currentUser().id;
            comment.authorLoginId = UserApp.currentUser().loginId;
            comment.authorName = UserApp.currentUser().name;
            Long commentId = IssueComment.create(comment);
            Issue.updateNumOfComments(issueId);

            // Attach all of the files in the current user's temporary storage.
            Attachment.attachFiles(UserApp.currentUser().id, project.id, Resource.ISSUE_COMMENT, commentId);

            return redirect(routes.IssueApp.issue(project.owner, project.name, issueId));
        }
    }

    public static Result deleteComment(String userName, String projectName, Long issueId,
            Long commentId) {
        Project project = ProjectApp.getProject(userName, projectName);
        IssueComment.delete(commentId);
        Issue.updateNumOfComments(issueId);
        Attachment.deleteAll(Resource.ISSUE_COMMENT, commentId);
        return redirect(routes.IssueApp.issue(project.owner, project.name, issueId));
    }

}
