package controllers;

import models.SimpleComment;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import play.data.Form;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;
import utils.Constants;
import utils.ErrorViews;

/**
 * {@link models.SimpleComment} CRUD 컨트롤러
 *
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
            return forbidden(ErrorViews.Forbidden.render("auth.unauthorized.comment"));
        }


        SimpleComment newComment = commentForm.get();
        newComment.resourceKey = resourceKey;
        newComment.authorInfos(UserApp.currentUser());
        newComment.save();

        return redirect;
    }

    public static Result deleteComment(Long id) {
        SimpleComment simpleComment = SimpleComment.findById(id);
        if(simpleComment == null) {
            notFound();
        }
        if (!AccessControl.isAllowed(UserApp.currentUser(), simpleComment.asResource(), Operation.DELETE)) {
            return forbidden(ErrorViews.Forbidden.render("auth.unauthorized.waringMessage"));
        }
        simpleComment.delete();
        String backPageUrl = request().getHeader("Referer");
        return redirect(backPageUrl);
    }

}
