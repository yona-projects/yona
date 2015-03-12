/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Changsung Kim
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
package models.support;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Junction;
import controllers.AbstractPostingApp;
import models.CommentThread;
import models.Project;
import models.ReviewComment;
import models.enumeration.Direction;
import org.apache.commons.lang.StringUtils;

import java.util.List;


/**
 * The class for searching, sorting and filtering in review menu of a project.
 */
public class ReviewSearchCondition extends AbstractPostingApp.SearchCondition implements Cloneable {
    public String state;
    public Long authorId;
    public Long participantId;

    public ReviewSearchCondition() {
        state = CommentThread.ThreadState.OPEN.name();
        orderBy = "createdDate";
    }

    /**
     * Returns the result after querying with the conditions to the model.
     *
     * It queries to the the model, {@link models.support.ReviewSearchCondition}, to search, sort and filter.
     *
     * @param project
     * @return The result of the query.
     */
    public ExpressionList<CommentThread> asExpressionList(Project project) {
        ExpressionList<CommentThread> el = CommentThread.find.where().eq("project.id", project.id);

        if (authorId != null) {
            el.eq("author.id", authorId);
        }

        if (participantId != null) {
            List<Object> ids = ReviewComment.find.where().eq("author.id", participantId).findIds();
            el.in("reviewComments.id", ids);
        }

        CommentThread.ThreadState threadState = CommentThread.ThreadState.valueOf(state.toUpperCase());
        el.eq("state", threadState);

        Direction direction = Direction.valueOf(orderDir.toUpperCase());
        if (StringUtils.isNotBlank(orderBy)) {
            el.orderBy(orderBy + " " + direction.name());
        }

        if (StringUtils.isNotBlank(filter)) {
            Junction<CommentThread> junction = el.disjunction();
            junction.icontains("reviewComments.contents", filter)
                    .icontains("commitId", filter)
                    .icontains("path", filter)
                    .endJunction();
        }

        return el;
    }

    public ReviewSearchCondition clone() {
        ReviewSearchCondition one = new ReviewSearchCondition();
        one.orderBy = this.orderBy;
        one.orderDir = this.orderDir;
        one.filter = this.filter;
        one.pageNum = this.pageNum;
        one.state = this.state;
        one.authorId = this.authorId;
        one.participantId = this.participantId;
        return one;
    }

    public ReviewSearchCondition setState(String state) {
        this.state = state;
        return this;
    }

    public ReviewSearchCondition setAuthorId(Long authorId) {
        this.authorId = authorId;
        return this;
    }

    public ReviewSearchCondition setParticipantId(Long participantId) {
        this.participantId = participantId;
        return this;
    }

}
