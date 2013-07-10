package controllers;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.SqlQuery;
import com.avaje.ebean.SqlRow;
import models.Label;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.List;

import static com.avaje.ebean.Expr.*;
import static play.libs.Json.toJson;

public class LabelApp extends Controller {
    private static final int MAX_FETCH_LABELS = 1000;

    /**
     * 태그 목록 요청에 대해 응답한다.
     *
     * when: 프로젝트 Overview 페이지에서 사용자가 태그를 추가하려고 할 때, 이름 자동완성을 위해 사용한다.
     *
     * 주어진 {@code query}로 태그를 검색하여, 그 목록을 json 형식으로 돌려준다.
     *
     * 돌려줄 태그 목록의 갯수가, {@link LabelApp#MAX_FETCH_LABELS}와 주어진 {@code limit}중에서 가장 작은 값보다
     * 크다면, 그 값에 의해 제한된다. 만약 제한이 되었다면, 응답의 @{code Content-Range}헤더로 어떻게 제한이
     * 되었는지에 대한 정보를 클라이언트에게 전달하게 된다. 예를 들어 10개 중에 8개만을 보내게 되었다면
     * @{code Content-Range: 8/10}이 된다. 자세한 것은 label-typeahead.md 문서의 "Content-Range 헤더" 문단을
     * 참조하라.
     *
     * 다음의 경우에는 {@code 406 Not Acceptable}로 응답한다.
     * 클라이언트가 {@code application/json}을 받아들일 수 없는 경우. 태그 목록 요청에 대한 성공적인 응답에서,
     * 엔터티 본문의 미디어 타입은 언제나 {@code application/json}이기 때문이다.
     *
     * @param query 태그에 대한 검색어 질의
     * @param limit 가져올 태그의 최대 갯수
     * @return 태그 목록 요청에 대한 응답
     * @see <a href="https://github.com/nforge/hive/blob/master/docs/technical/label-typeahead
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
}
