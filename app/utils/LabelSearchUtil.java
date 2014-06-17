/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Jungkook Kim
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
package utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.LabelOwner;
import models.resource.ResourceConvertible;

import play.mvc.Http.Request;

import com.avaje.ebean.Expr;
import com.avaje.ebean.Expression;
import com.avaje.ebean.Query;
import com.avaje.ebean.QueryResultVisitor;

public class LabelSearchUtil {
    private static final String FIELD_NAME_ID = "id";

    @SuppressWarnings("unchecked")
    public static Expression createLabelSearchExpression(final Query<? extends LabelOwner> query, final Set<Long> labelIds) {
        List<Object> ids = findIds((Query<LabelOwner>) query, labelIds);
        if (ids.isEmpty()) {
            return Expr.isNull(FIELD_NAME_ID);
        } else {
            return Expr.in(FIELD_NAME_ID, ids);
        }
    }

    public static Set<Long> getLabelIds(final Request request) {
        Set<Long> set = new HashSet<>();
        String[] labelIds = request.queryString().get("labelIds");
        if (labelIds != null) {
            for (String labelId : labelIds) {
                set.add(Long.valueOf(labelId));
            }
        }
        return set;
    }

    private static List<Object> findIds(final Query<LabelOwner> query, final Set<Long> labelIds) {
        final List<Object> ids = new ArrayList<>();
        query.copy().findVisit(new QueryResultVisitor<LabelOwner>() {
            /**
             * @see com.avaje.ebean.QueryResultVisitor#accept
             */
            @Override
            public boolean accept(LabelOwner labelOwner) {
                if (hasLabels(labelOwner, labelIds)) {
                    ids.add(labelOwner.asResource().getId());
                }
                return true;
            }
        });
        return ids;
    }

    private static boolean hasLabels(final LabelOwner labelOwner, final Set<Long> labelIds) {
        Set<Long> objectLabelIds = new HashSet<>();
        Set<? extends ResourceConvertible> labels = labelOwner.getLabels();
        for (ResourceConvertible label : labels) {
            objectLabelIds.add(Long.valueOf(label.asResource().getId()));
        }
        return objectLabelIds.containsAll(labelIds);
    }
}
