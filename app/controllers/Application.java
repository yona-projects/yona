/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Sangcheol Hwang
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
import models.Project;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import playRepository.RepositoryService;
import views.html.error.notfound_default;
import views.html.index.index;
import jsmessages.JsMessages;

import java.io.File;

public class Application extends Controller {

    @AnonymousCheck
    public static Result index() {
        return ok(index.render(UserApp.currentUser()));
    }

    public static Result removeTrailer(String paths){
        String path = request().path();
        if( path.charAt(path.length()-1) == '/' ) {
            path = path.substring(0, path.length() - 1);
        } else {
            Logger.error("Unexpected url call : " + request().path());
            return notFound(notfound_default.render("error.notfound"));
        }
        Logger.debug("Trailing slash removed and redirected: " + request().path() + " to " + path );
        return redirect(path);
    }

    public static Result init() {
        makeTestRepository();
        return redirect(routes.Application.index());
    }

    static final JsMessages messages = JsMessages.create(play.Play.application());

    public static Result jsMessages() {
        return ok(messages.generate("Messages")).as("application/javascript");
    }

    private static void makeTestRepository() {
        for (Project project : Project.find.all()) {
            Logger.debug("makeTestRepository: " + project.name);
            try {
                RepositoryService.createRepository(project);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static Result navi() {
        return ok(index.render(UserApp.currentUser()));
    }

    public static Result UIKit(){
        return ok(views.html.help.UIKit.render());
    }

    public static Result fake() {
        // Do not call this.
        return badRequest();
    }
}
