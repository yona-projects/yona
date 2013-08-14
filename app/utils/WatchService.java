package utils;

import com.avaje.ebean.annotation.Transactional;
import controllers.UserApp;
import models.Unwatch;
import models.User;
import models.Watch;
import models.enumeration.ResourceType;
import models.resource.Resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: nori
 * Date: 13. 8. 8
 * Time: 오후 1:25
 * To change this template use File | Settings | File Templates.
 */
public class WatchService {
    public static void watch(Resource resource) {
        watch(UserApp.currentUser(), resource);
    }

    @Transactional
    public static void watch(User user, Resource resource) {
        watch(user, resource.getType(), resource.getId().toString());
    }

    public static void watch(User user, ResourceType resourceType, String resourceId) {
        Watch watch = Watch.findBy(user, resourceType, resourceId);
        if (watch == null) {
            watch = new Watch();
            watch.user = user;
            watch.resourceId = resourceId;
            watch.resourceType = resourceType;
            watch.save();
        }

        Unwatch unwatch = Unwatch.findBy(user, resourceType, resourceId);
        if (unwatch != null) {
            unwatch.delete();
        }
    }

    public static void unwatch(Resource resource) {
        unwatch(UserApp.currentUser(), resource);
    }

    public static void unwatch(User user, Resource resource) {
        unwatch(user, resource.getType(), resource.getId().toString());
    }

    public static void unwatch(User user, ResourceType resourceType, String resourceId) {
        Unwatch unwatch = Unwatch.findBy(user, resourceType, resourceId);
        if (unwatch == null) {
            unwatch = new Unwatch();
            unwatch.user = user;
            unwatch.resourceId = resourceId;
            unwatch.resourceType = resourceType;
            unwatch.save();
        }

        Watch watch = Watch.findBy(user, resourceType, resourceId);
        if (watch != null) {
            watch.delete();
        }
    }

    public static Set<User> findWatchers(Resource target) {
        return findWatchers(target.getType(), target.getId().toString());
    }

    public static Set<User> findWatchers(ResourceType resourceType, String resourceId) {
        HashSet<User> users = new HashSet<>();
        for (Watch watch: Watch.findBy(resourceType, resourceId)) {
            users.add(watch.user);
        }
        return users;
    }

    public static Set<User> findUnwatchers(Resource target) {
        return findUnwatchers(target.getType(), target.getId().toString());
    }

    public static Set<User> findUnwatchers(ResourceType resourceType, String resourceId) {
        HashSet<User> users = new HashSet<>();
        for (Unwatch unwatch: Unwatch.findBy(resourceType, resourceId)) {
            users.add(unwatch.user);
        }
        return users;
    }

    public static List<String> findWatchedResourceIds(User user, ResourceType resourceType) {
        ArrayList<String> resourceIds = new ArrayList<>();
        for (Watch watch: Watch.findBy(user, resourceType)) {
            resourceIds.add(watch.resourceId);
        }
        for (Unwatch unwatch: Unwatch.findBy(user, resourceType)) {
            resourceIds.remove(unwatch.resourceId);
        }
        return resourceIds;
    }

    public static boolean isWatching(User user, ResourceType resourceType, String resourceId) {
        Watch watch = Watch.findBy(user, resourceType, resourceId);
        Unwatch unwatch = Unwatch.findBy(user, resourceType, resourceId);

        if (watch != null && unwatch == null) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isWatching(User user, Resource resource) {
        return isWatching(user, resource.getType(), resource.getId().toString());
    }

    public static boolean isWatching(Resource resource) {
        return isWatching(UserApp.currentUser(), resource.getType(), resource.getId().toString());
    }
}
