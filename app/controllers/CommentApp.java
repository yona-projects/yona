/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
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
import models.enumeration.Operation;
import models.resource.Resource;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;

import static models.enumeration.ResourceType.getValue;

@AnonymousCheck
public class CommentApp extends Controller {
    @Transactional
    public static Result delete(String type, String id) {
        Resource comment = Resource.get(getValue(type), id);

        if (comment == null) {
            return badRequest();
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), comment, Operation.DELETE)) {
            return forbidden();
        }

        switch(comment.getType()) {
            case COMMIT_COMMENT:
            case REVIEW_COMMENT:
                comment.delete();
                return ok();
            default:
                return badRequest();
        }
    }


}
