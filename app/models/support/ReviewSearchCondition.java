/*
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Changsung Kim
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


import models.*;
import java.util.List;
import com.avaje.ebean.Junction;
import models.enumeration.Direction;
import com.avaje.ebean.ExpressionList;
import controllers.AbstractPostingApp;
import org.apache.commons.lang.StringUtils;


/**
 * 프로젝트의 리뷰 메뉴에서 검색,정렬 및 필터를 위해 사용되는 클래스
 */
public class ReviewSearchCondition extends AbstractPostingApp.SearchCondition {
    public String state;
    public Long authorId;
    public Long participationId;

    public ReviewSearchCondition() {
        state = CommentThread.ThreadState.OPEN.name();
        orderBy = "createdDate";
    }

    /**
     * 검색, 정렬 및 필더 값으로 {@link models.support.ReviewSearchCondition} 모델에 쿼리를 하여 결과를 반환
     * @param project
     * @return
     */
    public ExpressionList<CommentThread> asExpressionList(Project project) {
        ExpressionList<CommentThread> el = CommentThread.find.where().eq("project.id", project.id);

        if (authorId != null) {
            el.eq("author.id", authorId);
        }

        if (participationId != null) {
            List<Object> ids = ReviewComment.find.where().eq("author.id", participationId).findIds();
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
        one.participationId = this.participationId;

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

    public ReviewSearchCondition setParticipationId(Long participationId) {
        this.participationId = participationId;
        return this;
    }

}
