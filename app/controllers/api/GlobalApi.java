/**
 *  Yona, 21st Century Project Hosting SW
 *  <p>
 *  Copyright Yona & Yobi Authors & NAVER Corp.
 *  https://yona.io
 **/

package controllers.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

public class GlobalApi extends Controller {
    public static Result hello() {
        ObjectNode json = Json.newObject();
        json.put("message", "I'm alive!");
        json.put("ok", true);
        return ok(json);
    }
}
