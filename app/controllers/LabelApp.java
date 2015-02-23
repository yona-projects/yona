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

import com.avaje.ebean.Ebean;
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.SqlQuery;
import com.avaje.ebean.SqlRow;
import controllers.annotation.AnonymousCheck;
import models.Label;
import org.apache.commons.lang3.StringUtils;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.avaje.ebean.Expr.icontains;
import static play.libs.Json.toJson;

@AnonymousCheck
public class LabelApp extends Controller {
    private static final int MAX_FETCH_LABELS = 1000;

    /**
     * @param category a group of label to search
     * @see <a href="https://github.com/nforge/yobi/blob/master/docs/technical/label-typeahead
     * .md>label-typeahead.md</a>
     */
    public static Result labels(String query, String category, Integer limit) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        ExpressionList<Label> el =
                Label.find.where().and(icontains("category", category), icontains("name", query));

        int total = el.findRowCount();

        if (total > limit) {
            el.setMaxRows(limit);
            response().setHeader("Content-Range", "items " + limit + "/" + total);
        }

        ArrayList<String> labels = new ArrayList<>();

        for (Label label: el.findList()) {
            labels.add(label.name);
        }

        return ok(toJson(labels));
    }

    public static Result categories(String query, Integer limit) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        SqlQuery sqlQuery;
        SqlQuery sqlCountQuery;

        if (query != null && query.length() > 0) {
            String sqlString =
                    "SELECT DISTINCT category FROM label WHERE lower(category) LIKE :category";
            sqlQuery = Ebean
                    .createSqlQuery(sqlString)
                    .setParameter("category", "%" + query.toLowerCase() + "%");
            sqlCountQuery = Ebean
                    .createSqlQuery("SELECT COUNT(*) AS cnt FROM (" + sqlString + ")")
                    .setParameter("category", "%" + query.toLowerCase() + "%");
        } else {
            String sqlString =
                    "SELECT DISTINCT category FROM label";
            sqlQuery = Ebean
                    .createSqlQuery(sqlString);
            sqlCountQuery = Ebean
                    .createSqlQuery("SELECT COUNT(*) AS cnt FROM (" + sqlString + ")");
        }

        int cnt = sqlCountQuery.findUnique().getInteger("cnt");

        if (limit > MAX_FETCH_LABELS) {
            limit = MAX_FETCH_LABELS;
        }

        if (cnt > limit) {
            sqlQuery.setMaxRows(limit);
            response().setHeader("Content-Range", "items " + limit + "/" + cnt);
        }

        List<String> categories = new ArrayList<>();
        for (SqlRow row: sqlQuery.findList()) {
            categories.add(row.getString("category"));
        }

        return ok(toJson(categories));
    }

    public static Set<Long> getLabelIds(final Http.Request request) {
        Set<Long> set = new HashSet<>();
        String[] labelIds = request.queryString().get("labelIds");
        if (labelIds != null) {
            for (String labelId : labelIds) {
                if(!StringUtils.isEmpty(labelId)) {
                    set.add(Long.valueOf(labelId));
                }
            }
        }
        return set;
    }
}
