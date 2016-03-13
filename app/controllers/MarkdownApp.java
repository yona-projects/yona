/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Changgun Kim
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

import com.fasterxml.jackson.databind.JsonNode;
import models.Project;
import play.mvc.Controller;
import play.mvc.Result;
import utils.Markdown;

public class MarkdownApp extends Controller {
    public static Result render(String ownerName, String projectName) {
        JsonNode requestJson = request().body().asJson();
        String body = requestJson.findPath("body").textValue();
        boolean breaks = requestJson.findPath("breaks").asBoolean();
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        return ok(Markdown.render(body, project, breaks));
    }

}
