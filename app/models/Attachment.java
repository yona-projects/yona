package models;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
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
    private static Finder<Long, Attachment> find = new Finder<Long, Attachment>(Long.class,
            Attachment.class);
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
                .eq("containerType", attach.containerType)
                .eq("containerId", attach.containerId).findUnique();
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

    public static void moveTempFiles(Long userId, Long projectId, Resource containerType,
            Long containerId) {
        // Move the attached files in the temporary area to the issue area.
        List<Attachment> attachments = Attachment.findTempFiles(userId);
        for (Attachment attachment : attachments) {
            attachment.projectId = projectId;
            attachment.containerType = containerType;
            attachment.containerId = containerId;
            attachment.save();
        }
    }

    public static String storeFile(File file) throws NoSuchAlgorithmException, IOException {
        // Compute sha1 checksum.
        MessageDigest algorithm = MessageDigest.getInstance("SHA1");
        DigestInputStream dis = new DigestInputStream(
                new BufferedInputStream(new FileInputStream(file)), algorithm);
        while (dis.read() != -1)
            ;
        Formatter formatter = new Formatter();
        for (byte b : algorithm.digest()) {
            formatter.format("%02x", b);
        }
        String hash = formatter.toString();

        // Store the file.
        File uploads = new File("uploads");
        if (!uploads.exists()) {
            uploads.mkdir();
        } else if (!uploads.isDirectory()) {
            throw new RuntimeException();
        }
        File saveFile = new File("uploads/" + formatter.toString());
        file.renameTo(saveFile);

        // Close all resources
        formatter.close();
        dis.close();

        return hash;
    }

    @Override
    public void save() {
        throw new UnsupportedOperationException("Use save(File file)");
    }

    // Return true only if the attachment record is created because there was
    // no same record.
    public boolean save(File file) throws NoSuchAlgorithmException, IOException {
        // Store the file in the filesystem and compute SHA1 hash.
        this.hash = Attachment.storeFile(file);

        // Add the attachment into the Database only if there is no same record.
        Attachment sameAttach = Attachment.findBy(this);
        if (sameAttach == null) {
            super.save();
            return true;
        } else {
            this.id = sameAttach.id;
            return false;
        }
    }

    public File getFile() {
        return new File("uploads/" + this.hash);
    }
}