package controllers;

import models.User;
import models.enumeration.Operation;
import models.resource.Resource;
import models.resource.ResourceParam;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;
import utils.WatchService;

public class WatchApp extends Controller {
    public static Result watch(ResourceParam resourceParam) {
        User user = UserApp.currentUser();
        Resource resource = resourceParam.resource;

        if (user.isAnonymous()) {
            return forbidden("Anonymous cannot watch it.");
        }

        if (!AccessControl.isAllowed(user, resource, Operation.READ)) {
            return forbidden("You have no permission to watch it.");
        }

        WatchService.watch(user, resource);

        return ok();
    }

    public static Result unwatch(ResourceParam resourceParam) {
        User user = UserApp.currentUser();
        Resource resource = resourceParam.resource;

        if (user.isAnonymous()) {
            return forbidden("Anonymous cannot unwatch it.");
        }

        WatchService.unwatch(user, resource);

        return ok();
    }
}
