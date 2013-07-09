package controllers;

import models.SimpleComment;
import models.enumeration.ResourceType;
import play.data.Form;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;
import utils.Constants;

/**
 * {@link models.SimpleComment} CRUD 컨트롤러
 *
 * @author Keesun Baik
 */
public class SimpleCommentApp extends Controller {

    public static Result newComment(String resourceKey) {
        Result redirect = redirect(request().getHeader("Referer"));

        Form<SimpleComment> commentForm = new Form<>(SimpleComment.class).bindFromRequest();
        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "board.comment.empty");
            return redirect;
        }

        if (!AccessControl.isCreatable(UserApp.currentUser(), ResourceType.SIMPLE_COMMENT)) {
            return forbidden(Messages.get("auth.unauthorized.comment"));
        }


        SimpleComment newComment = commentForm.get();
        newComment.resourceKey = resourceKey;
        newComment.authorInfos(UserApp.currentUser());
        newComment.save();

        return redirect;
    }

}
