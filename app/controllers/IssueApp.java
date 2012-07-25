/**
 * @author Taehyun Park
 */

package controllers;

import java.io.File;
import java.util.List;

import com.avaje.ebean.Page;

import models.Issue;
import models.IssueComment;
import models.User;
import models.enumeration.Direction;
import models.enumeration.IssueState;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Http.Request;
import play.mvc.Result;
import views.html.issue.issue;
import views.html.issue.issueList;
import views.html.issue.newIssue;
import views.html.issue.notExistingPage;

public class IssueApp extends Controller {

    public static final Project project = Project.findById(1L);

    /**
     * Display the paginated list of issues.
     * 
     * @param pageNum
     *            Current page number (starts from 0)
     * @param sortBy
     *            Column to be sorted
     * @param order
     *            Sort order (either asc or desc)
     * @param filter
     *            Filter applied on issue names
     */
    public static Result list(Long projectId, int pageNum, String sortBy,
            String order, String filter, String status, boolean commentedCheck,
            boolean fileAttachedCheck) {
        Page<Issue> issues = Issue.findIssues(projectId, pageNum,
                IssueState.getValue(status), sortBy, Direction.getValue(order),
                filter, commentedCheck, fileAttachedCheck);

        return ok(issueList.render("이슈 목록 조회", issues, projectId, sortBy,
                order, filter, status, commentedCheck, fileAttachedCheck, project));
    }

    public static Result search(Long projectId, String filter, int pageNum,
            String sortBy, String order, String status, boolean commentedCheck,
            boolean fileAttachedCheck) {
        Page<Issue> filteredIssues = Issue.findFilteredIssues(projectId,
                filter, IssueState.getValue(status), commentedCheck,
                fileAttachedCheck);

        return ok(issueList.render("검색된 이슈", filteredIssues, projectId, sortBy,
                order, filter, status, commentedCheck, fileAttachedCheck));

    }

    public static Result issue(Long issueId, Long projectId) {
        Issue issues = Issue.findById(issueId);
        List<IssueComment> comments = IssueComment
                .findCommentsByIssueId(issueId);
        if (issues == null) {
            return ok(notExistingPage.render("존재하지 않는 게시물", projectId, project));
        } else {
            Form<IssueComment> commentForm = new Form<IssueComment>(
                    IssueComment.class);
            return ok(issue.render("이슈 상세조회", issues, projectId, comments,
                    commentForm, project));
        }
    }

    public static Result newIssue(Long projectId) {
        return ok(newIssue.render("새 이슈", new Form<Issue>(Issue.class),
                projectId));
    }

    public static Result saveIssue(Long projectId) {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();

        if (issueForm.hasErrors()) {
            return badRequest(newIssue.render("ERRRRRRORRRRR!!!!", issueForm,
                    projectId));
        } else {
            Issue newIssue = issueForm.get();
            newIssue.reporter = UserApp.currentUser();
            // newIssue.commentCount = 0;
            newIssue.status = Issue.STATUS_ENROLLED;
            newIssue.setStatusType(newIssue.status);
            newIssue.filePath = saveFile(request());
            Issue.create(newIssue);
        }
        // TODO statusType 뭔가 이상함
        return redirect(routes.IssueApp.list(projectId,
                Issue.FIRST_PAGE_NUMBER, Issue.SORTBY_ID,
                Issue.ORDERBY_DESCENDING, "", "all", false, false));
    }

    public static Result delete(Long issueId, Long projectId) {
        Issue.delete(issueId);
        return redirect(routes.IssueApp.list(projectId,
                Issue.FIRST_PAGE_NUMBER, Issue.SORTBY_ID,
                Issue.ORDERBY_DESCENDING, "", "all", false, false));
    }

    public static Result saveComment(Long issueId, Long projectId) {
        Form<IssueComment> commentForm = new Form<IssueComment>(
                IssueComment.class).bindFromRequest();

        if (commentForm.hasErrors()) {
            return TODO;

        } else {
            IssueComment comment = commentForm.get();
            comment.issue = Issue.findById(issueId);
            comment.author = User.findByName("hobi");

            MultipartFormData body = request().body().asMultipartFormData();

            FilePart filePart = body.getFile("filePath");

            if (filePart != null) {
                File saveFile = new File("public/uploadFiles/"
                        + filePart.getFilename());
                filePart.getFile().renameTo(saveFile);

                comment.filePath = filePart.getFilename();
            }

            IssueComment.create(comment);

            return redirect(routes.IssueApp.issue(issueId, projectId));
        }
    }

    public static Result extractExcelFile() {
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
            File saveFile = new File("public/uploadFiles/"
                    + filePart.getFilename());
            filePart.getFile().renameTo(saveFile);
            return filePart.getFilename();
        }
        return null;
    }
}
