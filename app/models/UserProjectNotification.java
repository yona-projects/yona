/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package models;

import models.enumeration.EventType;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.*;

/**
 * Project notification subscribing settings with events which are customized by user
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id", "notification_type"}))
public class UserProjectNotification extends Model {

    private static final long serialVersionUID = 1L;

    public static final Finder<Long, UserProjectNotification> find = new Finder<>(Long.class, UserProjectNotification.class);

    @Id
    public Long id;

    @ManyToOne
    public User user;

    @ManyToOne
    public Project project;

    @Enumerated(EnumType.STRING)
    public EventType notificationType;

    public boolean allowed;

    public static Map<Project, Map<EventType, Boolean>> getProjectNotifications(User user) {
        Map<Project, Map<EventType, Boolean>> result = new HashMap<>();
        List<UserProjectNotification> list = find.where().eq("user", user).findList();
        for(UserProjectNotification noti : list) {
            Project notiProject = noti.project;
            Map<EventType, Boolean> pn = result.get(notiProject);
            if(pn == null) {
                pn = new HashMap<>();
                result.put(notiProject, pn);
            }
            pn.put(noti.notificationType, noti.allowed);
        }
        return result;
    }

    /**
     * Check whether the alarm which is respond to watching {@code project}'s {@code notiType}
     * is already being received or not.
     *
     * If there is no information about {@code project} in {@code notiMap}
     * or doesn't exist {@code project}'s {@code notiType},
     * then the method judge that it is already receiving the notification alarm.
     *
     * @param notiMap
     * @param project
     * @param notiType
     * @return
     */
    public static boolean isEnabledNotiType(Map<Project, Map<EventType, Boolean>> notiMap, Project project, EventType notiType) {
        if(isJustDefaultWatching(notiMap, project)) {
            return isNotifiedByDefault(notiType);
        }

        Map<EventType, Boolean> projectNoti = notiMap.get(project);
        if(isCustomizedByUser(notiType, projectNoti)) {
            return projectNoti.get(notiType);
        } else {
            return isNotifiedByDefault(notiType);
        }
    }

    private static boolean isCustomizedByUser(EventType notiType, Map<EventType, Boolean> projectNoti) {
        return projectNoti.containsKey(notiType);
    }

    private static boolean isJustDefaultWatching(Map<Project, Map<EventType, Boolean>> notiMap, Project project) {
        return !notiMap.containsKey(project);
    }

    public static UserProjectNotification findOne(User user, Project project, EventType notificationType) {
        return find.where()
                .eq("user", user)
                .eq("project", project)
                .eq("notificationType", notificationType.name())
                .findUnique();
    }

    public void toggle(EventType notificationType) {
        this.allowed = !this.allowed;
        if (allowed == isNotifiedByDefault(notificationType)) {
            delete();
        } else {
            update();
        }
    }

    public static void unwatchExplictly(User user, Project project, EventType notiType) {
        UserProjectNotification newOne = new UserProjectNotification();
        newOne.user = user;
        newOne.project = project;
        newOne.notificationType = notiType;
        newOne.allowed = false;
        newOne.save();
    }

    public static void watchExplictly(User user, Project project, EventType notiType) {
        UserProjectNotification newOne = new UserProjectNotification();
        newOne.user = user;
        newOne.project = project;
        newOne.notificationType = notiType;
        newOne.allowed = true;
        newOne.save();
    }

    /**
     *
     * Basically, if there is no information about {@code project}' {@code notiType}
     * then it judge it is already receiving notification alarm.
     *
     * @param user
     * @param project
     * @param eventType
     * @return
     */
    public static boolean isEnabledNotiType(User user, Project project, EventType eventType) {
        UserProjectNotification notification = findOne(user, project, eventType);
        return notification == null || notification.allowed;
    }

    public static boolean isNotifiedByDefault(EventType eventType) {
        switch (eventType) {
            case NEW_COMMENT:  // events not notified by project watch default
                return false;
            default:
                return true;
        }
    }

    public static Set<User> findEventWatchersByEventType(Long projectId, EventType eventType) {
        return findByEventTypeAndOption(projectId, eventType, true);
    }

    public static Set<User> findEventUnwatchersByEventType(Long projectId, EventType eventType) {
        return findByEventTypeAndOption(projectId, eventType, false);
    }

    private static Set<User> findByEventTypeAndOption(Long projectId, EventType eventType, boolean isAllowd) {
        List<UserProjectNotification> userProjectNotifications = find.where()
                .eq("project.id", projectId)
                .eq("notificationType", eventType)
                .eq("allowed", isAllowd)
                .findList();
        Set<User> users = new LinkedHashSet<>();
        for (UserProjectNotification notification : userProjectNotifications) {
            users.add(notification.user);
        }
        return users;
    }

    public static void deleteUnwatchedProjectNotifications(User user, Project project){
        List<UserProjectNotification> userProjectNotifications = find.where()
                .eq("user.id", user.id)
                .eq("project.id", project.id)
                .findList();
        for (UserProjectNotification notification : userProjectNotifications) {
            notification.delete();
        }
    }
}
