/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models;

import models.enumeration.EventType;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User this class when someone want to know whether a user is receiving notification alarm from the project or not
 *
 * @author Keesun Baik
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
        this.allowed = !this.allowed;
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
}
