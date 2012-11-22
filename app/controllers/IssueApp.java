/**
 * @author Taehyun Park
 */

package controllers;

import com.avaje.ebean.*;
import models.*;
import models.enumeration.*;
import models.support.*;
import play.data.*;
import play.mvc.*;
import utils.*;
import views.html.issue.*;

import java.io.*;
import java.util.Set;

public class IssueApp extends Controller {

    /**
     * 페이지 처리된 이슈들의 리스트를 보여준다.
     *
     * @param projectName
     *            프로젝트 이름
     * @param state
     *            이슈 해결 상태
     * @return
     */
    public static Result issues(String userName, String projectName, String state) {
        Project project = ProjectApp.getProject(userName, projectName);
        Form<SearchCondition> issueParamForm = new Form<SearchCondition>(SearchCondition.class);
        SearchCondition issueParam = issueParamForm.bindFromRequest().get();
        Page<Issue> issues = Issue.find(project.id, issueParam.pageNum,
                State.getValue(state), issueParam.sortBy,
                Direction.getValue(issueParam.orderBy), issueParam.filter, issueParam.milestone,
                issueParam.commentedCheck);
        return ok(issueList.render("title.issueList", issues, issueParam, project));
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

    public static Result newIssue(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        return ok(newIssue.render("title.newIssue", new Form<Issue>(Issue.class), project));
    }

    public static Result saveIssue(String ownerName, String projectName) throws IOException {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();
        Project project = ProjectApp.getProject(ownerName, projectName);
        if (issueForm.hasErrors()) {
            return badRequest(newIssue.render(issueForm.errors().toString(), issueForm, project));
        } else {
            Issue newIssue = issueForm.get();
            newIssue.date = JodaDateUtil.now();
            newIssue.authorId = UserApp.currentUser().id;
            newIssue.authorName = UserApp.currentUser().name;
            newIssue.project = project;
            newIssue.state = State.OPEN;

            String[] labelIds = request().body().asMultipartFormData().asFormUrlEncoded().get("labelIds[]");
            if (labelIds != null) {
                for (String labelId: labelIds) {
                    newIssue.labels.add(IssueLabel.findById(Long.parseLong(labelId)));
                }
            }

            Long issueId = Issue.create(newIssue);

            // Attach all of the files in the current user's temporary storage.
            Attachment.attachFiles(UserApp.currentUser().id, project.id, Resource.ISSUE_POST, issueId);
        }
        return redirect(routes.IssueApp.issues(project.owner, project.name,
                State.ALL.state()));
    }

    public static Result editIssue(String userName, String projectName, Long id) {
        Issue targetIssue = Issue.findById(id);
        Form<Issue> editForm = new Form<Issue>(Issue.class).fill(targetIssue);
        Project project = ProjectApp.getProject(userName, projectName);
        return ok(editIssue.render("title.editIssue", editForm, targetIssue, project));
    }

    public static Result updateIssue(String userName, String projectName, Long id) throws IOException {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);
        if (issueForm.hasErrors()) {
            return badRequest(issueForm.errors().toString());
        } else {
            Issue issue = issueForm.get();
            issue.id = id;
            issue.date = Issue.findById(id).date;
            issue.project = project;
            String[] labelIds = request().body().asMultipartFormData().asFormUrlEncoded().get("labelIds[]");
            if (labelIds != null) {
                for (String labelId: labelIds) {
                    issue.labels.add(IssueLabel.findById(Long.parseLong(labelId)));
                }
            }

            Issue.edit(issue);

            // Attach the files in the current user's temporary storage.
            Attachment.attachFiles(UserApp.currentUser().id, project.id, Resource.ISSUE_POST, id);
        }
        return redirect(routes.IssueApp.issues(project.owner, project.name, State.ALL.name()));
    }

    public static Result deleteIssue(String userName, String projectName, Long issueId) {
        Project project = ProjectApp.getProject(userName, projectName);

        Issue.delete(issueId);
        return redirect(routes.IssueApp.issues(project.owner, project.name,
                State.ALL.state()));
    }

    public static Result saveComment(String userName, String projectName, Long issueId) throws IOException {
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
        return redirect(routes.IssueApp.issue(project.owner, project.name, issueId));
    }

    public static Result extractExcelFile(String userName, String projectName, String state)
            throws Exception {
        Project project = ProjectApp.getProject(userName, projectName);
        Form<SearchCondition> issueParamForm = new Form<SearchCondition>(SearchCondition.class);
        SearchCondition issueParam = issueParamForm.bindFromRequest().get();
        Page<Issue> issues = Issue.find(project.id, issueParam.pageNum,
                State.getValue(state), issueParam.sortBy,
                Direction.getValue(issueParam.orderBy), issueParam.filter, issueParam.milestone,
                issueParam.commentedCheck);
        Issue.excelSave(issues.getList(), project.name + "_" + state + "_filter_"
                + issueParam.filter + "_milestone_" + issueParam.milestone);
        return ok(issueList.render("title.issueList", issues, issueParam, project));
    }

    public static Result enrollAutoNotification(String userName, String projectName)
            throws Exception {
        return TODO;
    }

    public static Result getIssueDatil(){
    	return TODO;
    }
}
