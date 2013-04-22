package controllers;

import com.avaje.ebean.ExpressionList;
import models.Project;
import models.Tag;
import models.enumeration.Operation;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.AccessControl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static play.libs.Json.toJson;

/**
 * Created with IntelliJ IDEA.
 * User: nori
 * Date: 13. 4. 12
 * Time: 오후 3:37
 * To change this template use File | Settings | File Templates.
 */
public class TagApp extends Controller {
    private static final int MAX_FETCH_TAGS = 1000;

    public static Result tags(String query) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        ExpressionList<Tag> el = Tag.find.where().contains("name", query);
        int total = el.findRowCount();
        if (total > MAX_FETCH_TAGS) {
            el.setMaxRows(MAX_FETCH_TAGS);
            response().setHeader("Content-Range", "items " + MAX_FETCH_TAGS + "/" + total);
        }

        Map<Long, String> tags = new HashMap<Long, String>();
        for (Tag tag: el.findList()) {
            tags.put(tag.id, tag.name);
        }

        return ok(toJson(tags));
    }

}
