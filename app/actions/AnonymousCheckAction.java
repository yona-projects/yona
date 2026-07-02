/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Wansoon Park, Keesun Baek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package actions;

import controllers.UserApp;
import controllers.annotation.AnonymousCheck;
import controllers.routes;
import play.mvc.Action;
import play.mvc.Http.Request;
import play.mvc.Result;
import java.util.concurrent.*;
import utils.AccessControl;
import utils.AccessLogger;
import utils.Constants;
import utils.LegacyRequestContext;

/**
 * After execute {@link actions.AbstractProjectCheckAction},
 * If current user is anonymous, redirect to the login page.
 *
 * @author Wansoon Park, Keesun Beak
 *
 */
public class AnonymousCheckAction extends Action<AnonymousCheck> {

    @Override
    public CompletionStage<Result> call(Request request) {
        return LegacyRequestContext.withRequest(request, () -> {
            if ((AccessControl.isAnonymousNotAllowed() || configuration.requiresLogin()) &&
                    UserApp.currentUser().isAnonymous()) {
                if (configuration.displaysFlashMessage()) {
                    LegacyRequestContext.flash().put(Constants.WARNING, "user.login.alert");
                }
                String loginFormUrl = routes.UserApp.loginForm().url();
                loginFormUrl += "?redirectUrl=" + request.path();
                CompletionStage<Result> promise = CompletableFuture.completedFuture(redirect(loginFormUrl));
                AccessLogger.log(request, promise, null);
                return promise;
            }
            return delegate.call(request);
        });
    }

}
