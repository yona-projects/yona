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
import play.libs.F.Promise;
import play.mvc.Http.Context;
import play.mvc.Result;
import utils.ErrorViews;

public class CodeAccessCheckAction extends AbstractProjectCheckAction<Void> {
    @Override
    protected Promise<Result> call(Project project, Context context, PathParser parser) throws Throwable {
        // Only members can access code?
        Promise<Result> promise;
        if(project.isCodeAccessibleMemberOnly && !project.hasMember(UserApp.currentUser())) {
            promise = Promise.pure((Result) forbidden(ErrorViews.Forbidden.render("error.forbidden.or.notfound", context.request().path())));
            return promise;
        }
        return this.delegate.call(context);
    }
}
