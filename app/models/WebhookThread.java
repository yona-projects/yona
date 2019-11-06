/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models;

import com.avaje.ebean.annotation.Transactional;

import models.enumeration.EventType;
import models.enumeration.State;
import models.enumeration.ResourceType;
import models.resource.Resource;

import play.db.ebean.Model;
import play.data.validation.Constraints.Required;

import javax.annotation.Nonnull;
import javax.persistence.*;
import java.util.List;
import java.util.Date;

@Entity
public class WebhookThread extends Model {
    private static final long serialVersionUID = 1L;
    public static Finder<Long, WebhookThread> find = new Finder<>(Long.class, WebhookThread.class);

    @Id
    public Long id;

    @ManyToOne
    public Webhook webhook;

    @Required
    @Enumerated(EnumType.STRING)
    public ResourceType resourceType;

    @Required
    public String resourceId;

    @Required
    public String threadId;

    public Date createdAt;


    public WebhookThread(Long webhookId, Resource resource, String threadId) {
        this.webhook = Webhook.findById(webhookId);
        this.resourceType = resource.getType();
        this.resourceId = resource.getId();
        this.threadId = threadId;
        this.createdAt = new Date();
    }

    @Transactional
    public static void create(Long webhookId, Resource resource, String threadId) {
        WebhookThread webhookthread = new WebhookThread(webhookId, resource, threadId);
        webhookthread.save();
        // TODO : Raise appropriate error when required field is empty
    }
    public WebhookThread getWebhookThread(Long webhookId, Resource resource) {
        return find.where()
                .eq("webhook.id", webhookId)
                .eq("resourceType", resource.getType())
                .eq("resource.id", resource.getId())
                .findUnique();
    }

}
