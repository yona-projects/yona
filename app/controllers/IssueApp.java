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

    public static Result issueList(int pageNum, int status) {
        Page<Issue> page = null;
        if (status == Issue.STATUS_OPEN) {
            page = Issue.findOnlyOpenIssues(pageNum);
        } else if (status == Issue.STATUS_CLOSED) {
            page = Issue.findOnlyClosedIssues(pageNum);
        } else if (status == Issue.STATUS_NONE) {
            page = Issue.findOnePage(pageNum);
        } else {
            page = Issue.findOnePage(pageNum);
        }

        return ok(issueList.render("이슈", page));
    }

    public static Result newIssue() {
        return ok(newIssue.render("새 이슈", new Form<Issue>(Issue.class)));
    }

    // public static Result findByTitle(){
    //
    // }

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

    public static Result issue(Long issueId) {
        Issue issue = Issue.findById(issueId);
        List<IssueComment> comments = IssueComment
                .findCommentsByIssueId(issueId);
        if (issue == null) {
            return ok(notExistingPage.render("존재하지 않는 게시물"));
            // return ok(notExistingPage.render("xxxxx"));
        } else {
            Form<IssueComment> commentForm = new Form<IssueComment>(
                    IssueComment.class);
            return ok(views.html.issue.issue.render(issue, comments,
                    commentForm));
        }
    }

    public static Result delete(Long issueId) {
        Issue.delete(issueId);
        return redirect(routes.IssueApp.issueList(6));
    }

}
