package models;

import models.enumeration.NotificationType;
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
    public NotificationType notificationType;

    public boolean allowed;

    public static Map<Project, Map<NotificationType, Boolean>> getProjectNotifications(User user) {
        Map<Project, Map<NotificationType, Boolean>> result = new HashMap<>();
        List<UserProjectNotification> list = find.where().eq("user", user).findList();
        for(UserProjectNotification noti : list) {
            Project notiProject = noti.project;
            Map<NotificationType, Boolean> pn = result.get(notiProject);
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
    public static boolean isAllowed(Map<Project, Map<NotificationType, Boolean>> notiMap, Project project, NotificationType notiType) {
        if(!notiMap.containsKey(project)) {
            return true;
        }

        Map<NotificationType, Boolean> projectNoti = notiMap.get(project);
        if(!projectNoti.containsKey(notiType)) {
            return true;
        } else {
            return projectNoti.get(notiType);
        }
    }

    public static UserProjectNotification findOne(User user, Project project, NotificationType notificationType) {
        return find.where()
                .eq("user", user)
                .eq("project", project)
                .eq("notificationType", notificationType)
                .findUnique();
    }

    public void change() {
        if(this.allowed == false) {
            this.allowed = true;
        } else {
            this.allowed = false;
        }
        update();
    }

    public static void saveNewOff(User user, Project project, NotificationType notiType) {
        UserProjectNotification newOne = new UserProjectNotification();
        newOne.user = user;
        newOne.project = project;
        newOne.notificationType = notiType;
        newOne.allowed = false;
        newOne.save();
    }
}
