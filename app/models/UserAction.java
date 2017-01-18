/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models;

import models.enumeration.ResourceType;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.List;

@MappedSuperclass
abstract public class UserAction extends Model {
    private static final long serialVersionUID = 7150871138735757127L;
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
        List<T> list = finder.where()
                .eq("user.id", subject.id)
                .eq("resourceType", resourceType)
                .eq("resourceId", resourceId).findList();
        if(list.isEmpty()){
            return null;
        } else {
            return list.get(0);
        }
    }

    public static <T extends UserAction> List<T> findBy(Finder<Long, T> finder, User subject,
                                                           ResourceType resourceType) {
        return finder.where()
                .eq("user.id", subject.id)
                .eq("resourceType", resourceType).findList();
    }

    public static <T extends UserAction> int countBy(Finder<Long, T> finder,
                                                        ResourceType resourceType, String resourceId) {
        return finder.where()
                .eq("resourceType", resourceType)
                .eq("resourceId", resourceId).findRowCount();
    }
}
