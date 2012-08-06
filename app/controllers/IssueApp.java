/**
 * @author Taehyun Park
 */

package controllers;

import java.io.File;
import java.util.List;

import com.avaje.ebean.Page;

import models.Issue;
import models.IssueComment;
import models.Milestone;
import models.Post;
import models.User;
import models.Project;
import models.enumeration.Direction;
import models.enumeration.IssueState;
import models.enumeration.IssueStateType;
import models.support.SearchCondition;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Http.Request;
import play.mvc.Result;
import views.html.issue.editIssue;
import views.html.issue.issue;
import views.html.issue.issueList;
import views.html.issue.newIssue;
import views.html.issue.notExistingPage;
import views.html.issue.issueError;

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
    public static Result issues(String projectName, String stateType) {
        Project project = Project.findByName(projectName);
        Form<SearchCondition> issueParamForm = new Form<SearchCondition>(SearchCondition.class);
        SearchCondition issueParam = issueParamForm.bindFromRequest().get();
        if (project == null) {
            return notFound();
        }
        Page<Issue> issues = Issue.findIssues(project.name, issueParam.pageNum,
                IssueStateType.getValue(stateType), issueParam.sortBy,
                Direction.getValue(issueParam.orderBy), issueParam.filter, issueParam.milestone,
                issueParam.commentedCheck, issueParam.fileAttachedCheck);

        return ok(issueList.render("title.issueList", issues, issueParam, project));
    }

    public static Result issue(String projectName, Long issueId) {
        Project project = Project.findByName(projectName);
        if (project == null) {
            return notFound();
        }
        Issue issueInfo = Issue.findById(issueId);
        if (issueInfo == null) {
            return ok(notExistingPage.render("존재하지 않는 게시물", project));
        } else {
            Form<IssueComment> commentForm = new Form<IssueComment>(IssueComment.class);
            return ok(issue.render("title.issueDetail", issueInfo, commentForm, project));
        }
    }

    public static Result newIssue(String projectName) {
        Project project = Project.findByName(projectName);
        if (project == null) {
            return notFound();
        }
        return ok(newIssue.render("title.newIssue", new Form<Issue>(Issue.class), project));
    }

    public static Result saveIssue(String projectName) {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();
        Project project = Project.findByName(projectName);

        if (project == null) {
            return notFound();
        }
        if (issueForm.hasErrors()) {
            return badRequest(newIssue.render(issueForm.errors().toString(), issueForm, project));
        } else {
            Issue newIssue = issueForm.get();
            newIssue.reporterId = UserApp.currentUser().id;
            newIssue.project = project;
            newIssue.state = IssueState.ENROLLED;
            if (issueForm.get().milestoneId == null) {
                newIssue.milestoneId = "none";
            }
            newIssue.updateStatusType(newIssue.state);
            newIssue.filePath = saveFile(request());
            Issue.create(newIssue);
            
            Logger.debug("IssueApp : saveIssue - milestoneId:"+newIssue.milestoneId);
            Logger.debug("IssueApp : saveIssue - state:"+newIssue.state);
            Logger.debug("IssueApp : saveIssue - stateType:"+newIssue.stateType);
            Logger.debug("IssueApp : saveIssue - assigneeId:"+newIssue.assigneeId);
        }
        return redirect(routes.IssueApp.issues(project.name, IssueStateType.ALL.stateType()));
    }

    public static Result editIssue(String projectName, Long id) {
        Issue targetIssue = Issue.findById(id);
        Form<Issue> editForm = new Form<Issue>(Issue.class).fill(targetIssue);
        Project project = Project.findByName(projectName);
        if (UserApp.currentUser().id == targetIssue.reporterId) {
            return ok(editIssue.render("title.editIssue", editForm, id, project));
        } else {
            return ok(issueError.render("post.edit.rejectNotAuthor",
                    routes.IssueApp.issue(project.name, id), project));
        }
    }

    public static Result updateIssue(String projectName, Long id) {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();
        Project projcet = Project.findByName(projectName);

        if (issueForm.hasErrors()) {
            return badRequest(issueForm.errors().toString());
        } else {

            Issue issue = issueForm.get();
            issue.reporterId = UserApp.currentUser().id;
            issue.id = id;
            issue.filePath = saveFile(request());
            issue.project = projcet;

            Issue.edit(issue);
        }

        return redirect(routes.IssueApp.issues(projcet.name, IssueStateType.ALL.name()));
    }

    public static Result delete(String projectName, Long issueId) {
        Project project = Project.findByName(projectName);
        if (project == null) {
            return notFound();
        }
        Issue.delete(issueId);
        return redirect(routes.IssueApp.issues(project.name, IssueStateType.ALL.stateType()));
    }

    public static Result saveComment(String projectName, Long issueId) {
        Form<IssueComment> commentForm = new Form<IssueComment>(IssueComment.class)
                .bindFromRequest();

        Project project = Project.findByName(projectName);
        if (commentForm.hasErrors()) {
            return TODO;

        } else {
            IssueComment comment = commentForm.get();
            comment.issue = Issue.findById(issueId);
            comment.authorId = UserApp.currentUser().id;
            comment.filePath = saveFile(request());
            IssueComment.create(comment);

            return redirect(routes.IssueApp.issue(project.name, issueId));
        }
    }

    public static Result extractExcelFile(String projectName) {
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
