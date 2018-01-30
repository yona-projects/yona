/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package notification;

import models.User;
import models.enumeration.EventType;
import models.enumeration.ResourceType;
import models.resource.Resource;
import play.api.i18n.Lang;

import java.util.Date;
import java.util.Set;

public interface INotificationEvent {
    User getSender();

    Resource getResource();

    String getMessage(Lang lang);

    String getPlainMessage(Lang lang);

    String getUrlToView();

    Date getCreatedDate();

    String getTitle();

    EventType getType();

    ResourceType getResourceType();

    String getResourceId();

    boolean resourceExists();

    Set<User> findReceivers();

    void setReceivers(Set<User> b);
}
