/**
 *  Yona, 21st Century Project Hosting SW
 *  <p>
 *  Copyright Yona & Yobi Authors & NAVER Corp.
 *  https://yona.io
 **/
package actions;

import actions.support.PathParser;
import controllers.UserApp;
import models.Project;
import java.util.concurrent.*;
import play.mvc.Http.Request;
import play.mvc.Result;
import utils.ErrorViews;

public class CodeAccessCheckAction extends AbstractProjectCheckAction<Void> {
    @Override
    protected CompletionStage<Result> call(Project project, Request request, PathParser parser) {
        // Only members can access code?
        CompletionStage<Result> promise;
        if(project.isCodeAccessibleMemberOnly && !project.hasMember(UserApp.currentUser())) {
            promise = CompletableFuture.completedFuture((Result) forbidden(ErrorViews.Forbidden.render("error.forbidden.or.notfound", request.path())));
            return promise;
        }
        return this.delegate.call(request);
    }
}
