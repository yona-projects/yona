package controllers;

import models.*;
import models.enumeration.Direction;
import models.enumeration.Operation;

import play.data.Form;
import play.mvc.Call;
import play.mvc.Content;
import utils.AccessControl;

import play.mvc.Controller;
import play.mvc.Result;
import utils.Callback;
import utils.Constants;

import java.io.IOException;

public class AbstractPostingApp extends Controller {
    public static final int ITEMS_PER_PAGE = 15;

    public static class SearchCondition {
        public String orderBy;
        public String orderDir;
        public String filter;
        public int pageNum;

        public SearchCondition() {
            this.orderDir = Direction.DESC.direction();
            this.orderBy = "id";
            this.filter = "";
            this.pageNum = 1;
        }
    }

    public static Result newComment(Comment comment, Form<? extends Comment> commentForm, Call redirectTo, Callback updateCommentContainer) throws IOException {
        if (session(UserApp.SESSION_USERID) == null) {
            flash(Constants.WARNING, "user.login.alert");
            return redirect(redirectTo);
        }

        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "board.comment.empty");
            return redirect(redirectTo);
        }

        comment.setAuthor(UserApp.currentUser());
        updateCommentContainer.run(); // this updates comment.issue or comment.posting;
        Project project = comment.asResource().getProject();
        comment.save();

        // Attach all of the files in the current user's temporary storage.
        Attachment.attachFiles(UserApp.currentUser().id, comment.asResource());

        return redirect(redirectTo);
    }

    public static Result deletePosting(AbstractPosting posting, Call redirectTo) {
        if (!AccessControl.isAllowed(UserApp.currentUser(), posting.asResource(), Operation.DELETE)) {
            return forbidden();
        }

        posting.delete();

        Attachment.deleteAll(posting.asResource().getType(), posting.id);

        return redirect(redirectTo);
    }

    public static Result deleteComment(Comment comment, Call redirectTo) {
        if (!AccessControl.isAllowed(UserApp.currentUser(), comment.asResource(), Operation.DELETE)) {
            return forbidden();
        }

        comment.delete();

        Attachment.deleteAll(comment.asResource().getType(), comment.id);

        return redirect(redirectTo);
    }

    protected static Result editPosting(AbstractPosting original, AbstractPosting posting, Form<? extends AbstractPosting> postingForm, Call redirectTo, Callback updatePosting) {
        if (postingForm.hasErrors()) {
            return badRequest(postingForm.errors().toString());
        }

        posting.id = original.id;
        posting.createdDate = original.createdDate;
        posting.authorId = original.authorId;
        posting.authorLoginId = original.authorLoginId;
        posting.authorName = original.authorName;
        posting.project = original.project;
        updatePosting.run();
        posting.update();

        // Attach the files in the current user's temporary storage.
        Attachment.attachFiles(UserApp.currentUser().id, original.asResource());

        return redirect(redirectTo);
    }

    public static Result newPostingForm(Project project, Content content) {
        if (UserApp.currentUser() == UserApp.anonymous) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        return ok(content);
    }

}
