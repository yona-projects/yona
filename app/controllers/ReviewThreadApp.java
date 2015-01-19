/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Changsung Kim, Keesun Baik
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

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;
import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import models.CommentThread;
import models.Project;
import models.enumeration.Operation;
import models.support.ReviewSearchCondition;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.reviewthread.list;

/**
 * @author Changsung Kim
 * @author Keesun Baik
 */
@AnonymousCheck
public class ReviewThreadApp extends Controller {

    public static final int REVIEWS_PER_PAGE = 15;

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(value = Operation.READ)
    public static Result reviewThreads(String ownerName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        ReviewSearchCondition searchCondition = Form.form(ReviewSearchCondition.class).bindFromRequest().get();
        ExpressionList<CommentThread> el = searchCondition.asExpressionList(project);
        Page<CommentThread> commentThreads = el.findPagingList(REVIEWS_PER_PAGE).getPage(searchCondition.pageNum - 1);
        return ok(list.render(project, commentThreads, searchCondition));
    }

}
