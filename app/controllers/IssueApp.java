/**
 * @author Taehyun Park
 */

package controllers;

import java.io.File;

import models.Issue;
import models.IssueComment;
import models.Project;
import models.enumeration.Direction;
import models.enumeration.IssueState;
import models.enumeration.StateType;
import models.support.SearchCondition;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Http.Request;
import play.mvc.Result;
import utils.Constants;
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
     * @param statusType
     *            이슈 해결 상태
     * @return
     */
    public static Result issues(String userName, String projectName, String stateType) {
        Project project = ProjectApp.getProject(userName, projectName);
        Form<SearchCondition> issueParamForm = new Form<SearchCondition>(SearchCondition.class);
        SearchCondition issueParam = issueParamForm.bindFromRequest().get();
        Page<Issue> issues = Issue.findIssues(project.name, issueParam.pageNum,
                StateType.getValue(stateType), issueParam.sortBy,
                Direction.getValue(issueParam.orderBy), issueParam.filter, issueParam.milestone,
                issueParam.commentedCheck, issueParam.fileAttachedCheck);
        return ok(issueList.render("title.issueList", issues, issueParam, project));
    }

    public static Result issue(String userName, String projectName, Long issueId) {
        Project project = ProjectApp.getProject(userName, projectName);
        Issue issueInfo = Issue.findById(issueId);
        if (issueInfo == null) {
            return ok(notExistingPage.render("title.post.notExistingPage", project));
        } else {
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

    public static Result saveIssue(String userName, String projectName) {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);
        if (issueForm.hasErrors()) {
            return badRequest(newIssue.render(issueForm.errors().toString(), issueForm, project));
        } else {
            Issue newIssue = issueForm.get();
            newIssue.authorId = UserApp.currentUser().id;
            newIssue.project = project;
            newIssue.state = IssueState.ENROLLED;
//            newIssue.updateStatusType();
            newIssue.filePath = saveFile(request());
            Issue.create(newIssue);
        }
        return redirect(routes.IssueApp.issues(project.owner, project.name,
                StateType.ALL.stateType()));
    }

    public static Result editIssue(String userName, String projectName, Long id) {
        Issue targetIssue = Issue.findById(id);
        Form<Issue> editForm = new Form<Issue>(Issue.class).fill(targetIssue);
        Project project = ProjectApp.getProject(userName, projectName);
        return ok(editIssue.render("title.editIssue", editForm, id, project));
    }

    public static Result updateIssue(String userName, String projectName, Long id) {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);
        if (issueForm.hasErrors()) {
            return badRequest(issueForm.errors().toString());
        } else {
            Issue issue = issueForm.get();
//            issue.authorId = UserApp.currentUser().id;
            issue.id = id;
            issue.date = Issue.findById(id).date;
            issue.filePath = saveFile(request());
            issue.project = project;
            issue.updateState(issue);

            Logger.error("assigneeId :_"+issue.assigneeId +"// diagnoisResult:_" + issue.diagnosisResult);
            //            issue.updateStateType();
            Issue.edit(issue);
        }
        return redirect(routes.IssueApp.issues(project.owner, project.name, StateType.ALL.name()));
    }

    public static Result deleteIssue(String userName, String projectName, Long issueId) {
        Project project = ProjectApp.getProject(userName, projectName);

        Issue.delete(issueId);
        return redirect(routes.IssueApp.issues(project.owner, project.name,
                StateType.ALL.stateType()));
    }

    public static Result saveComment(String userName, String projectName, Long issueId) {
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
            comment.filePath = saveFile(request());
            IssueComment.create(comment);
            Issue.updateNumOfComments(issueId);
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

    public static Result extractExcelFile(String userName, String projectName, String stateType)
            throws Exception {
        Project project = ProjectApp.getProject(userName, projectName);
        Form<SearchCondition> issueParamForm = new Form<SearchCondition>(SearchCondition.class);
        SearchCondition issueParam = issueParamForm.bindFromRequest().get();
        Page<Issue> issues = Issue.findIssues(project.name, issueParam.pageNum,
                StateType.getValue(stateType), issueParam.sortBy,
                Direction.getValue(issueParam.orderBy), issueParam.filter, issueParam.milestone,
                issueParam.commentedCheck, issueParam.fileAttachedCheck);
        Issue.excelSave(issues.getList(), project.name + "_" + stateType + "_filter_"
                + issueParam.filter + "_milestone_" + issueParam.milestone);
        return ok(issueList.render("title.issueList", issues, issueParam, project));
    }

    public static Result enrollAutoNotification(String userName, String projectName)
            throws Exception {
        return TODO;
    }

    /**
     * From BoardApp
     * 
     * @param request
     * @return
     */
    private static String saveFile(Request request) {
        MultipartFormData body = request.body().asMultipartFormData();

        FilePart filePart = body.getFile("filePath");

        if (filePart != null) {
            File saveFile = new File("public/uploadFiles/" + filePart.getFilename());
            filePart.getFile().renameTo(saveFile);
            return filePart.getFilename();
        }
        return null;
    }
}
