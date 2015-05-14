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

import com.google.common.base.Joiner;
import models.User;
import models.enumeration.EventType;
import models.enumeration.ResourceType;
import models.resource.Resource;
import play.api.i18n.Lang;

import javax.annotation.Nonnull;
import java.util.*;

public class MergedNotificationEvent implements INotificationEvent {
    private final List<INotificationEvent> messageSources;
    private final INotificationEvent main;
    private Set<User> receivers;

    public MergedNotificationEvent(@Nonnull INotificationEvent main,
                                   @Nonnull List<INotificationEvent> messageSources) {
        this.main = main;
        this.messageSources = new LinkedList<>(messageSources);
    }

    public MergedNotificationEvent(@Nonnull INotificationEvent main) {
        this(main, Arrays.asList(main));
    }

    @Override
    public User getSender() {
        return main.getSender();
    }

    @Override
    public Resource getResource() {
        return main.getResource();
    }

    @Override
    public String getMessage(Lang lang) {
        List<String> messages = new ArrayList<>();
        for(INotificationEvent event : messageSources) {
            messages.add(event.getMessage(lang));
        }
        return Joiner.on("\n\n---\n\n").join(messages);
    }

    @Override
    public String getUrlToView() {
        return main.getUrlToView();
    }

    @Override
    public Date getCreatedDate() {
        return main.getCreatedDate();
    }

    @Override
    public String getTitle() {
        return main.getTitle();
    }

    @Override
    public EventType getType() {
        return main.getType();
    }

    @Override
    public ResourceType getResourceType() {
        return main.getResourceType();
    }

    @Override
    public String getResourceId() {
        return main.getResourceId();
    }

    @Override
    public boolean resourceExists() {
        return main.resourceExists();
    }

    @Override
    public Set<User> findReceivers() {
        if (receivers != null) {
            return receivers;
        } else {
            return main.findReceivers();
        }
    }

    @Override
    public void setReceivers(@Nonnull Set<User> receivers) {
        this.receivers = receivers;
    }

    public List<INotificationEvent> getMessageSources() {
        return messageSources;
    }
}
