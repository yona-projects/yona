/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
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

    public static WebhookThread create(Long webhookId, Resource resource, String threadId) {
        WebhookThread webhookthread = new WebhookThread(webhookId, resource, threadId);
        webhookthread.save();
        // TODO : Raise appropriate error when required field is empty
        return webhookthread;
    }

    public static WebhookThread getWebhookThread(Long webhookId, Resource resource) {
        return find.where()
                .eq("webhook.id", webhookId)
                .eq("resourceType", resource.getType())
                .eq("resourceId", resource.getId())
                .findUnique();
    }

    @Override
    public String toString() {
        return "Webhook{" +
                "id=" + id +
                ", webhook=" + webhook +
                ", resourceType=" + resourceType +
                ", resourceId=" + resourceId +
                ", threadId=" + threadId +
                ", createdAt=" + createdAt +
                '}';
    }
}
