package controllers;

import com.avaje.ebean.ExpressionList;
import models.Project;
import models.Tag;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.List;

import static com.avaje.ebean.Expr.*;
import static play.libs.Json.toJson;

public class TagApp extends Controller {
    private static final int MAX_FETCH_TAGS = 1000;

    public enum Context {
        PROJECT_TAGGING_TYPEAHEAD, DEFAULT
    }

    /**
     * 태그 목록 요청에 대해 응답한다.
     *
     * when: 프로젝트 Overview 페이지에서 사용자가 태그를 추가하려고 할 때, 이름 자동완성을 위해 사용한다.
     *
     * 주어진 {@code query}로 태그를 검색하여, 그 목록을 json 형식으로 돌려준다. 이 때 {@code contextAsString}로
     * 주어진 컨텍스트를 고려한다. 예를 들어 {@code "PROJECT_TAGGING_TYPEAHEAD"}가 주어진 경우에는, 프로젝트에
     * 태그를 붙이려고 하는 상황에서 자동완성을 위해 태그의 목록을 요청한 경우이다. 이 때 만약 그 프로젝트에 라이선스
     * 태그가 하나도 붙어있지 않다면, 사용자가 라이선스를 붙이는 것을 유도하기 위해 목록에서 라이선스가 앞에 나오도록
     * 순서를 조절한다.
     *
     * 돌려줄 태그 목록의 갯수가, {@link TagApp#MAX_FETCH_TAGS}와 주어진 {@code limit}중에서 가장 작은 값보다
     * 크다면, 그 값에 의해 제한된다. 만약 제한이 되었다면, 응답의 @{code Content-Range}헤더로 어떻게 제한이
     * 되었는지에 대한 정보를 클라이언트에게 전달하게 된다. 예를 들어 10개 중에 8개만을 보내게 되었다면
     * @{code Content-Range: 8/10}이 된다. 자세한 것은 tag-typeahead.md 문서의 "Content-Range 헤더" 문단을
     * 참조하라.
     *
     * 다음중 하나의 경우에는 {@code 400 Bad Request}로 응답한다.
     * 1. 요청에 서버가 이해할 수 없는 context 값이 들어있다.
     * 2. 요청에 {@code context}가 요구하는 파라메터의 값이 없거나 잘못되어있다. 예를 들어,
     * {@link Context#PROJECT_TAGGING_TYPEAHEAD}는 정수로 된 프로젝트 아이디를 요구한다.
     *
     * 다음의 경우에는 {@code 406 Not Acceptable}로 응답한다.
     * 클라이언트가 {@code application/json}을 받아들일 수 없는 경우. 태그 목록 요청에 대한 성공적인 응답에서,
     * 엔터티 본문의 미디어 타입은 언제나 {@code application/json}이기 때문이다.
     *
     * @param query 태그에 대한 검색어 질의
     * @param contextAsString 어떤 상황에서 요청한 것인지에 대한 컨텍스트 정보
     * @param limit 가져올 태그의 최대 갯수
     * @return 태그 목록 요청에 대한 응답
     * @see <a href="https://github.com/nforge/hive/blob/master/docs/technical/tag-typeahead.md>tag-typeahead.md</a>
     */
    public static Result tags(String query, String contextAsString, Integer limit) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        List<String> tags;

        Context context = null;

        if (contextAsString == null || contextAsString.isEmpty()) {
            context = Context.DEFAULT;
        } else {
            try {
                context = Context.valueOf(contextAsString);
            } catch (IllegalArgumentException e) {
                return badRequest("Invalid context '" + contextAsString + "'");
            }
        }

        if (limit == null || limit > MAX_FETCH_TAGS) {
            limit = MAX_FETCH_TAGS;
        }

        switch(context) {
            case PROJECT_TAGGING_TYPEAHEAD:
                try {
                    Long projectId  = Long.valueOf(request().getQueryString("project_id"));
                    Project project = Project.find.byId(projectId);
                    if (project == null) {
                        return badRequest("No project matches given project_id '" + projectId + "'");
                    }
                    tags = tagsForProjectTagging(query, limit, project);
                } catch (IllegalArgumentException e) {
                    return badRequest("In " + Context.PROJECT_TAGGING_TYPEAHEAD + " context, " +
                            "the query string should have a project_id field in integer type.");
                }
                break;
            default:
                tags = tagsForDefault(query, limit);
                break;
        }

        return ok(toJson(tags));
    }

    /**
     * 태그 목록을 반환한다.
     *
     * @param el 태그의 목록에 대한 질의
     * @return 태그 목록
     */
    private static List<String> tags(ExpressionList<Tag> el) {
        ArrayList<String> tags = new ArrayList<String>();

        for (Tag tag: el.findList()) {
            tags.add(tag.toString());
        }

        return tags;
    }

    /**
     * {@link Context#DEFAULT} 컨텍스트에서의 태그의 목록을 반환하며, Content-Range 헤더를 알맞게 설정한다.
     *
     * when: 컨텍스트 없이 태그의 목록을 가져오는 경우 사용된다.
     *
     * {@link Tag#category}혹은 {@link Tag#name}가 문자열 {@code query}을 포함하고 있는 모든 {@link Tag}의
     * 목록을 반환한다. 단 그 갯수가 {@code limit}보다 큰 경우 그 값에 의해 제한되며, 제한이 된 경우에는 응답의
     * @{code Content-Range} 헤더에 그 사실이 기술된다. 예를 들어 10개 중에 8개만을 보내게 되었다면
     * @{code Content-Range: 8/10}이 된다. 자세한 것은 tag-typeahead.md 문서의 "Content-Range 헤더" 문단을
     * 참조하라.
     *
     * @param query 태그에 대한 검색어 질의
     * @param limit 목록의 최대 갯수
     * @return 태그 목록
     */
    private static List<String> tagsForDefault(String query, int limit) {
        ExpressionList<Tag> el =
                Tag.find.where().or(icontains("category", query), icontains("name", query));

        int total = el.findRowCount();

        if (total > limit) {
            el.setMaxRows(limit);
            response().setHeader("Content-Range", "items " + limit + "/" + total);
        }

        return tags(el);
    }

    /**
     * {@link Context#PROJECT_TAGGING_TYPEAHEAD} 컨텍스트에서의 태그의 목록을 반환하며, Content-Range 헤더를
     * 알맞게 설정한다.
     *
     * when: 프로젝트 Overview 페이지에서 사용자가 태그를 추가하려고 할 때, {@link TagApp#tags}에 의해 호출된다.
     *
     * 1. 돌려줄 태그의 갯수가 많지 않아 {@code limit}으로 제한할 필요가 없거나, 그 프로젝트에 라이선스 태그가 하나
     * 이상 붙어있다면  {@link TagApp#tagsForDefault(String, int)}를 호출하여 컨텍스트가 주어지지 않았을 때와
     * 같은 목록을 반환한다.
     * 2. 만약 제한을 해야 하는 상황이고 그 프로젝트에 라이선스 태그가 하나도 붙어있지 않다면 라이선스 태그들을 우선한다.
     * 사용자가 프로젝트에 라이선스 태그를 붙이는 것을 유도하기 위함이다.
     *
     * {@code Content-Range} 헤더의 설정에 대한 것은 {@link TagApp#tagsForDefault(String, int)} )}와 같다.
     *
     * @param query
     * @param limit
     * @param project
     * @return
     */
    private static List<String> tagsForProjectTagging(String query, int limit, Project project) {
        ExpressionList<Tag> el =
                Tag.find.where().or(icontains("category", query), icontains("name", query));

        int total = el.findRowCount();

        // If the limit is bigger than the total number of resulting tags, juts return all of them.
        if (limit > total) {
            return tagsForDefault(query, limit);
        }

        // If the project has no License tag, list License tags first to
        // recommend add one of them.
        boolean hasLicenseTags =
                Tag.find.where().eq("projects.id", project.id).eq("category", "License")
                        .findRowCount() > 0;

        if (hasLicenseTags) {
            return tagsForDefault(query, limit);
        }

        ExpressionList<Tag> elLicense =
                Tag.find.where().and(eq("category", "License"), icontains("name", query));
        elLicense.setMaxRows(limit);
        List<String> tags = tags(elLicense);

        // If every license tags are listed but quota still remains, then add
        // any other tags not in License category to the list.
        if (elLicense.findRowCount() < limit) {
            ExpressionList<Tag> elExceptLicense =
                    Tag.find.where().and(
                            ne("category", "License"),
                            or(icontains("category", query), icontains("name", query)));

            elExceptLicense.setMaxRows(limit - elLicense.findRowCount());

            for (Tag tag: elExceptLicense.findList()) {
                tags.add(tag.toString());
            }
        }

        if (tags.size() < total) {
            response().setHeader("Content-Range", "items " + tags.size() + "/" + total);
        }

        return tags;
    }
}
