package controllers;

import play.mvc.Controller;
import play.mvc.Result;

public class NotificationApp extends Controller {
    public static Result notifications(int from, int limit) {
        return ok(views.html.index.partial_notifications.render(from, limit));
    }
}
