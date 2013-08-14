package models;

import models.enumeration.ResourceType;

import javax.persistence.*;
import java.util.List;

@Entity
public class Watch extends UserAction {
    private static final long serialVersionUID = 1L;

    public static Finder<Long, Watch> find = new Finder<>(Long.class, Watch.class);

    public static List<Watch> findBy(ResourceType resourceType, String resourceId) {
        return findBy(find, resourceType, resourceId);
    }

    public static Watch findBy(User watcher, ResourceType resourceType, String resourceId) {
        return findBy(find, watcher, resourceType, resourceId);
    }

    public static List<Watch> findBy(User user, ResourceType resourceType) {
        return findBy(find, user, resourceType);
    }
}
