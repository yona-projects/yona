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
import play.mvc.Http.MultipartFormData.FilePart;
import views.html.issue.*;

import com.avaje.ebean.Page;

public class IssueApp extends Controller {

    public static final Project project = Project.findById(1L);

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
        return ok(issueList.render("이슈",
                Issue.page(page, 10, sortBy, order, filter, status), sortBy,
                order, filter, status, project));
    }

    public static Result saveIssue() {
        Form<Issue> issueForm = new Form<Issue>(Issue.class).bindFromRequest();

        if (issueForm.hasErrors()) {
            return ok("입력값이 잘못되었습니다.");
        } else {
            Issue issue = issueForm.get();
            issue.userId = UserApp.userId();
            issue.commentCount = 0;
            issue.setStatusType(issueForm.get().status);

            MultipartFormData body = request().body().asMultipartFormData();

            FilePart filePart = body.getFile("filePath");

            if (filePart != null) {
                File saveFile = new File("public/uploadFiles/"
                        + filePart.getFilename());
                filePart.getFile().renameTo(saveFile);
                issue.filePath = filePart.getFilename();
            }
            Issue.create(issue);
        }
        return redirect(routes.IssueApp.list(1, "id", "DESC", "",
                issueForm.get().statusType));
    }

    public static Result newIssue() {
        return ok(newIssue.render("새 이슈", new Form<Issue>(Issue.class), project));
    }

    public static Result issue(Long issueId) {
        Issue issue = Issue.findById(issueId);
        List<IssueComment> comments = IssueComment
                .findCommentsByIssueId(issueId);
        if (issue == null) {
            return ok(notExistingPage.render("존재하지 않는 게시물", project));
            // return ok(notExistingPage.render("xxxxx"));
        } else {
            Form<IssueComment> commentForm = new Form<IssueComment>(
                    IssueComment.class);
            return ok(issueDetail.render(issue, comments, commentForm, project));
        }
    }

    // TODO 5->0
    public static Result delete(Long issueId) {
        Issue.delete(issueId);
        return redirect(routes.IssueApp.list(0, "id", "DESC", "", 5));
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
    
    public static Result extractExcelFile(){
        return TODO;
    }
  


}
