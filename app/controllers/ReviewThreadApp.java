/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Changsung Kim, Keesun Baik
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

import actions.AnonymousCheckAction;
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;
import controllers.annotation.IsAllowed;
import models.CommentThread;
import models.Project;
import models.enumeration.Operation;
import models.support.ReviewSearchCondition;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import views.html.reviewthread.list;

/**
 * 리뷰 스레드 요청을 처리하는 컨트롤러.
 *
 * @author Changsung Kim
 * @author Keesun Baik
 */
public class ReviewThreadApp extends Controller {

    public static final int REVIEWS_PER_PAGE = 15;

    /**
     * 프로젝트 속한 리뷰 스레드 목록을 보여준다.
     *
     * @param ownerName
     * @param projectName
     * @return
     */
    @With(AnonymousCheckAction.class)
    @IsAllowed(value = Operation.READ)
    public static Result reviewThreads(String ownerName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        ReviewSearchCondition searchCondition = Form.form(ReviewSearchCondition.class).bindFromRequest().get();
        ExpressionList<CommentThread> el = searchCondition.asExpressionList(project);
        Page<CommentThread> commentThreads = el.findPagingList(REVIEWS_PER_PAGE).getPage(searchCondition.pageNum - 1);
        return ok(list.render(project, commentThreads, searchCondition));
    }

}
