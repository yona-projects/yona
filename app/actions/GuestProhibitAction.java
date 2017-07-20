/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package actions;

import controllers.UserApp;
import controllers.annotation.GuestProhibit;
import controllers.routes;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Http.Context;
import play.mvc.Result;
import utils.AccessControl;
import utils.AccessLogger;
import utils.Constants;

/**
 * After execute {@link AbstractProjectCheckAction},
 * If current user is anonymous, redirect to the login page.
 *
 * @author Wansoon Park, Keesun Beak
 *
 */
public class GuestProhibitAction extends Action<GuestProhibit> {

    @Override
    public Promise<Result> call(Context context) throws Throwable {
        if (UserApp.currentUser().isGuest) {
            if (configuration.displaysFlashMessage()) {
                play.mvc.Controller.flash(Constants.WARNING, "error.forbidden.or.not.allowed");
            }
            Promise<Result> promise = Promise.pure(redirect(routes.Application.index()));
            AccessLogger.log(context.request(), promise, null);
            return promise;
        }
        return delegate.call(context);
    }
}
