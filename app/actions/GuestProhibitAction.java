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
import java.util.concurrent.*;
import play.mvc.Action;
import play.mvc.Http.Request;
import play.mvc.Result;
import utils.AccessControl;
import utils.AccessLogger;
import utils.Constants;
import utils.LegacyRequestContext;

/**
 * After execute {@link AbstractProjectCheckAction},
 * If current user is anonymous, redirect to the login page.
 *
 * @author Wansoon Park, Keesun Beak
 *
 */
public class GuestProhibitAction extends Action<GuestProhibit> {

    @Override
    public CompletionStage<Result> call(Request request) {
        return LegacyRequestContext.withRequest(request, () -> {
            if (UserApp.currentUser().isGuest) {
                if (configuration.displaysFlashMessage()) {
                    LegacyRequestContext.flash().put(Constants.WARNING, "error.forbidden.or.not.allowed");
                }
                CompletionStage<Result> promise = CompletableFuture.completedFuture(redirect(routes.Application.index()));
                AccessLogger.log(request, promise, null);
                return promise;
            }
            return delegate.call(request);
        });
    }
}
