/**
 * @author Taehyun Park
 */

package controllers;

import java.io.File;
import java.util.List;

import models.*;
import play.data.Form;
import play.mvc.*;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.Request;
import play.mvc.Http.MultipartFormData.FilePart;
import views.html.issue.*;

import com.avaje.ebean.Page;

public class IssueApp extends Controller {

    /**
     * Display the paginated list of issues.
     * 
     * @param page
     *            Current page number (starts from 0)
     * @param sortBy
     *            Column to be sorted
     * @param order
     *            Sort order (either asc or desc)
     * @param filter
     *            Filter applied on issue names
     */
    public static Result list(int page, String sortBy, String order,
            String filter, int status) {
        return ok(issueList.render("이슈", Issue.page(page,
                Issue.ISSUE_COUNT_PER_PAGE, sortBy, order, filter, status),
                sortBy, order, filter, status));
    }

    public static Result saveIssue() {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();

        if (issueForm.hasErrors()) {
            return badRequest(newIssue.render("에러났슈", issueForm));
        } else {
            Issue newIssue = issueForm.get();
            newIssue.userId = UserApp.userId();
            newIssue.commentCount = 0;
            newIssue.status = Issue.STATUS_ENROLLED;
            newIssue.setStatusType(newIssue.status);
            newIssue.filePath = saveFile(request());
            Issue.create(newIssue);
        }
        return redirect(routes.IssueApp.list(Issue.FIRST_PAGE_NUMBER,
                Issue.SORTBY_ID, Issue.ORDERBY_DESCENDING, "",
                issueForm.get().statusType));
    }

    public static Result newIssue() {
        return ok(newIssue.render("새 이슈", new Form<Issue>(Issue.class)));
    }

    public static Result issue(Long issueId) {
        Issue issue = Issue.findById(issueId);
        List<IssueComment> comments = IssueComment
                .findCommentsByIssueId(issueId);
        if (issue == null) {
            return ok(notExistingPage.render("존재하지 않는 게시물"));
        } else {
            Form<IssueComment> commentForm = new Form<IssueComment>(
                    IssueComment.class);
            return ok(issueDetail.render(issue, comments, commentForm));
        }
    }

    // TODO 5->0
    public static Result delete(Long issueId) {
        Issue.delete(issueId);
        return redirect(routes.IssueApp.list(Issue.FIRST_PAGE_NUMBER,
                Issue.SORTBY_ID, Issue.ORDERBY_DESCENDING, "",
                Issue.STATUS_NONE));
    }

    public static Result saveComment(Long issueId) {
        Form<IssueComment> commentForm = new Form<IssueComment>(
                IssueComment.class).bindFromRequest();

        if (commentForm.hasErrors()) {
            return TODO;

        } else {
            IssueComment comment = commentForm.get();
            comment.issueId = issueId;
            comment.userId = User.findByName("hobi").id;

            MultipartFormData body = request().body().asMultipartFormData();

            FilePart filePart = body.getFile("filePath");

            if (filePart != null) {
                File saveFile = new File("public/uploadFiles/"
                        + filePart.getFilename());
                filePart.getFile().renameTo(saveFile);

                comment.filePath = filePart.getFilename();
            }

            IssueComment.create(comment);

            return redirect(routes.IssueApp.issue(issueId));
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
