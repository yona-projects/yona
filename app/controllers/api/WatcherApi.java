/**
 * Yona, 21st Century Project Hosting SW
 *
 * Copyright 2016 the original author or authors.
 */

package controllers.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import org.apache.commons.lang3.StringUtils;
import play.db.ebean.Transactional;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import utils.RouteUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static play.libs.Json.toJson;

public class WatcherApi extends Controller {

    @Transactional
    public static Result getWatchers(String owner, String projectName, Long number) {
        final int LIMIT = 100;
        int counter = 0;

        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        AbstractPosting posting = null;
        String type = request().getQueryString("type");
        if (StringUtils.isNotEmpty(type) && type.equals("issues")) {
            posting = AbstractPosting.findByNumber(Issue.finder, project, number);
        } else if (StringUtils.isNotEmpty(type) && type.equals("posts")){
            posting = AbstractPosting.findByNumber(Posting.finder, project, number);
        } else {
            return ok();
        }

        Set<User> watchers = posting.getWatchers();
        ObjectNode json = Json.newObject();
        List<ObjectNode> watcherList = new ArrayList<>();

        for(User user: watchers){
            counter++;

            ObjectNode watcher = Json.newObject();
            watcher.put("name", user.name);
            watcher.put("url", RouteUtil.getUrl(user));
            watcherList.add(watcher);
            if (counter == LIMIT) {
                break;
            }
        }

        json.put("totalWatchers", watchers.size());
        json.put("watchersInList", counter);
        json.put("watchers", toJson(watcherList));
        return ok(json);
    }

}
