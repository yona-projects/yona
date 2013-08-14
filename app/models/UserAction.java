package models;

import models.enumeration.ResourceType;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.List;

@MappedSuperclass
abstract public class UserAction extends Model {
    @Id
    public Long id;

    @ManyToOne
    public User user;

    @Enumerated(EnumType.STRING)
    public models.enumeration.ResourceType resourceType;

    public String resourceId;

    public static <T extends UserAction> List<T> findBy(Finder<Long, T> finder,
                                             ResourceType resourceType, String resourceId) {
        return finder.where()
                .eq("resourceType", resourceType)
                .eq("resourceId", resourceId).findList();
    }

    public static <T extends UserAction> T findBy(Finder<Long, T> finder, User subject,
                                      ResourceType resourceType, String resourceId) {
        return finder.where()
                .eq("user.id", subject.id)
                .eq("resourceType", resourceType)
                .eq("resourceId", resourceId).findUnique();
    }

    public static <T extends UserAction> List<T> findBy(Finder<Long, T> finder, User subject,
                                                           ResourceType resourceType) {
        return finder.where()
                .eq("user.id", subject.id)
                .eq("resourceType", resourceType).findList();
    }
}
