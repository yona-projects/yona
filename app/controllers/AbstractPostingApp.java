package controllers;

import play.db.ebean.Model;

import models.resource.Resource;

import models.*;
import models.enumeration.Direction;
import models.enumeration.Operation;
import models.enumeration.ResourceType;

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

    public static Result newComment(Comment comment, Form<? extends Comment> commentForm, Call redirectTo, Callback containerUpdater) throws IOException {
        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "board.comment.empty");
            return redirect(redirectTo);
        }

        comment.setAuthor(UserApp.currentUser());
        containerUpdater.run(); // this updates comment.issue or comment.posting;
        comment.save();

        // Attach all of the files in the current user's temporary storage.
        Attachment.attachFiles(UserApp.currentUser().id, comment.asResource());

        return redirect(redirectTo);
    }

    protected static Result delete(Model target, Resource resource, Call redirectTo) {
        if (!AccessControl.isAllowed(UserApp.currentUser(), resource, Operation.DELETE)) {
            return forbidden();
        }

        target.delete();

        return redirect(redirectTo);
    }

    protected static Result editPosting(AbstractPosting original, AbstractPosting posting, Form<? extends AbstractPosting> postingForm, Call redirectTo, Callback updatePosting) {
        if (postingForm.hasErrors()) {
            return badRequest(postingForm.errors().toString());
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), original.asResource(), Operation.UPDATE)) {
            return forbidden(views.html.project.unauthorized.render(original.project));
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

    public static Result newPostingForm(Project project, ResourceType resourceType, Content content) {
        if (!AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, resourceType)) {
            return forbidden(views.html.project.unauthorized.render(project));
        }

        return ok(content);
    }

}
