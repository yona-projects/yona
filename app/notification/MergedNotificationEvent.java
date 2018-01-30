/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
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
    public String getPlainMessage(Lang lang) {
        List<String> messages = new ArrayList<>();
        for(INotificationEvent event : messageSources) {
            messages.add(event.getPlainMessage(lang));
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
