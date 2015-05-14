/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2015 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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
