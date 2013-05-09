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

    private static List<String> tags(ExpressionList<Tag> el) {
        ArrayList<String> tags = new ArrayList<String>();

        for (Tag tag: el.findList()) {
            tags.add(tag.toString());
        }

        return tags;
    }

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
