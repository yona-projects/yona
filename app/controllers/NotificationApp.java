package controllers;

import models.NotificationEvent;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static play.libs.Json.toJson;

public class NotificationApp extends Controller {
    public static Result notifications(int from, int limit) {
        return ok(views.html.index.partial_notifications.render(from, limit));
    }
}
