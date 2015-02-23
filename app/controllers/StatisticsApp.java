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

import actions.DefaultProjectCheckAction;
import controllers.annotation.AnonymousCheck;
import models.Project;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import views.html.project.statistics;

@AnonymousCheck
public class StatisticsApp extends Controller {

    @With(DefaultProjectCheckAction.class)
    public static Result statistics(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        return ok(statistics.render("statistics", project));
    }

}
