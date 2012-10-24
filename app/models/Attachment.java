package models;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;

import models.enumeration.Resource;

import play.data.validation.*;

import play.db.ebean.Model;

@Entity
public class Attachment extends Model {
    /**
     *
     */
    private static final long serialVersionUID = 7856282252495067924L;
    private static Finder<Long, Attachment> find = new Finder<Long, Attachment>(
        Long.class, Attachment.class);
    @Id
    public Long id;

    @Constraints.Required
    public String name;

    @Constraints.Required
    public String hash;

    public Long projectId;

    @Enumerated(EnumType.STRING)
    public Resource containerType;

    public String mimeType;

    public Long containerId;

    public static List<Attachment> findTempFiles(Long userId) {
        return find.where()
                .eq("containerType", Resource.USER)
                .eq("containerId", userId).findList();
    }

    public static Attachment findBy(Attachment attach) {
        return find.where()
                .eq("name", attach.name)
                .eq("hash", attach.hash)
                .eq("projectId", attach.projectId)
                .eq("containerType",attach.containerType)
                .eq("containerId", attach.containerId).findUnique();
    }

    public static Attachment findBy(String name, String hash, Long projectId, Resource containerType, Long containerId) {
        return find.where()
                .eq("name", name)
                .eq("hash", hash)
                .eq("projectId", projectId)
                .eq("containerType",containerType)
                .eq("containerId", containerId).findUnique();
    }

    public static boolean exists(String hash) {
        return find.where().eq("hash", hash).findRowCount() > 0;
    }

    public static Attachment findById(Long id) {
        return find.byId(id);
    }
   public static List<Attachment> findByContainer(Resource containerType, Long containerId) {
       return find.where()
               .eq("containerType", containerType)
               .eq("containerId", containerId).findList();
   }

    public static void moveTempFiles(Long userId, Long projectId, Resource containerType, Long containerId) {
        // Move the attached files in the temporary area to the issue area.
        List<Attachment> attachments = Attachment.findTempFiles(userId);
        for(Attachment attachment : attachments) {
            attachment.projectId = projectId;
            attachment.containerType = containerType;
            attachment.containerId = containerId;
            attachment.save();
        }
    }
}