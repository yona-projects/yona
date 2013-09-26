package models;

import models.enumeration.EventType;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 어떤 사용자가 어떤 프로젝트의 어떤 알림 이벤트를 받고 싶어하는지 나타내는 클래스
 *
 * @author Keesun Baik
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id", "notificationType"}))
public class UserProjectNotification extends Model {

    private static final long serialVersionUID = 1L;

    public static Finder<Long, UserProjectNotification> find = new Finder<>(Long.class, UserProjectNotification.class);

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
     * {@code notiMap}에서 지켜보는 중인 {@code project}의 {@code notiType}에 해당하는 알림을 받고 있는지 확인한다.
     *
     * {@code notiMap}에 {@code project}에 대한 정보가 없거나,
     * {@code project}의 {@code notiType}에 대한 정보가 없다면
     * 기본적으로 알림을 받는 중으로 인식한다.
     *
     * @param notiMap
     * @param project
     * @param notiType
     * @return
     */
    public static boolean isEnabledNotiType(Map<Project, Map<EventType, Boolean>> notiMap, Project project, EventType notiType) {
        if(!notiMap.containsKey(project)) {
            return true;
        }

        Map<EventType, Boolean> projectNoti = notiMap.get(project);
        if(!projectNoti.containsKey(notiType)) {
            return true;
        } else {
            return projectNoti.get(notiType);
        }
    }

    public static UserProjectNotification findOne(User user, Project project, EventType notificationType) {
        return find.where()
                .eq("user", user)
                .eq("project", project)
                .eq("notificationType", notificationType)
                .findUnique();
    }

    public void toggle() {
        if(this.allowed == false) {
            this.allowed = true;
        } else {
            this.allowed = false;
        }
        update();
    }

    public static void unwatchExplictly(User user, Project project, EventType notiType) {
        UserProjectNotification newOne = new UserProjectNotification();
        newOne.user = user;
        newOne.project = project;
        newOne.notificationType = notiType;
        newOne.allowed = false;
        newOne.save();
    }

    /**
     * {@code user}가 {@code project}의 {@code notificationType}에 해당하는 알림을 받고 있는지 확인한다.
     *
     * {@code project}의 {@code notiType}에 대한 정보가 없다면
     * 기본적으로 알림을 받는 중으로 인식한다.
     *
     * @param user
     * @param project
     * @param eventType
     * @return
     */
    public static boolean isEnabledNotiType(User user, Project project, EventType eventType) {
        UserProjectNotification notification = findOne(user, project, eventType);
        if (notification == null || notification.allowed) {
            return true;
        }
        return notification.allowed;
    }
}
