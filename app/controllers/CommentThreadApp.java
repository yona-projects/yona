/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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
package controllers;

import controllers.annotation.AnonymousCheck;
import models.CommentThread;
import models.NotificationEvent;
import models.enumeration.Operation;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;

import static models.CommentThread.ThreadState.CLOSED;
import static models.CommentThread.ThreadState.OPEN;

@AnonymousCheck
public class CommentThreadApp extends Controller {

    @Transactional
    public static Result updateState(Long id, CommentThread.ThreadState state) {
        CommentThread thread = CommentThread.find.byId(id);

        if (thread == null) {
            return notFound();
        }

        Operation operation;

        switch(state) {
            case OPEN:
                operation = Operation.REOPEN;
                break;
            case CLOSED:
                operation = Operation.CLOSE;
                break;
            default:
                throw new UnsupportedOperationException();
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), thread.asResource(), operation)) {
            return forbidden();
        }

        CommentThread.ThreadState previousState = thread.state;
        thread.state = state;
        thread.update();

        try {
            NotificationEvent.afterStateChanged(previousState, thread);
        } catch (Exception e) {
            play.Logger.warn(
                    "Failed to send a notification for a change of review thread's state", e);
        }

        return ok();
    }

    public static Result open(Long id) {
        return updateState(id, OPEN);
    }

    public static Result close(Long id) {
        return updateState(id, CLOSED);
    }
}
