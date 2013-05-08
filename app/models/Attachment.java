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

import models.resource.Resource;
import org.apache.commons.io.FileUtils;
import org.apache.tika.Tika;

import models.enumeration.ResourceType;

import play.data.validation.*;

import play.db.ebean.Model;
import scalax.file.NotDirectoryException;

@Entity
public class Attachment extends Model {
    private static final long serialVersionUID = 7856282252495067924L;
    public static Finder<Long, Attachment> find = new Finder<Long, Attachment>(Long.class,
            Attachment.class);
    private static String uploadDirectory = "uploads";
    @Id
    public Long id;

    @Constraints.Required
    public String name;

    @Constraints.Required
    public String hash;

    public Long projectId;

    @Enumerated(EnumType.STRING)
    public ResourceType containerType;

    public String mimeType;

    public Long size;

    public Long containerId;

    /**
     * 모든 임시파일은 컨테이너 타입 ResourceType.USER 에 해당한다.
     *
     * @param userId
     * @return
     */
    public static List<Attachment> findUserFiles(Long userId) {
        return find.where()
                .eq("containerType", ResourceType.USER)
                .eq("containerId", userId).findList();
    }

    private static Attachment findBy(Attachment attach) {
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

    public static List<Attachment> findByContainer(
            ResourceType containerType, Long containerId) {
        return find.where()
                .eq("containerType", containerType)
                .eq("containerId", containerId).findList();
    }

    public static int countByContainer(ResourceType containerType, Long containerId) {
        return find.where()
                .eq("containerType", containerType)
                .eq("containerId", containerId).findRowCount();
    }

    public static List<Attachment> findByContainer(Resource resource) {
        return findByContainer(resource.getType(), resource.getId());
    }

    // Attach the files from the user's temporary area to the given container.
    public static int attachFiles(
            Long userId, Long projectId, ResourceType containerType, Long containerId) {
        // Move the attached files in the temporary area to the issue area.
        List<Attachment> attachments = Attachment.findByContainer(ResourceType.USER, userId);
        for (Attachment attachment : attachments) {
            attachment.projectId = projectId;
            attachment.containerType = containerType;
            attachment.containerId = containerId;
            attachment.save();
        }
        return attachments.size();
    }

    public static int attachFiles(Long userId, Resource container) {
        return attachFiles(userId, container.getProject().id,
                container.getType(), container.getId());
    }

    // Store the files in the filesystem.
    private static String moveFileIntoUploadDirectory(File file)
            throws NoSuchAlgorithmException, IOException {
        // Compute sha1 checksum.
        MessageDigest algorithm = MessageDigest.getInstance("SHA1");
        DigestInputStream dis = new DigestInputStream(
                new BufferedInputStream(new FileInputStream(file)), algorithm);
        while (dis.read() != -1);
        Formatter formatter = new Formatter();
        for (byte b : algorithm.digest()) {
            formatter.format("%02x", b);
        }
        String hash = formatter.toString();

        // Store the file.
        // Before do that, create upload directory if it doesn't exist.
        File uploads = new File(uploadDirectory);
        uploads.mkdirs();
        if (!uploads.isDirectory()) {
            formatter.close();
            dis.close();
            throw new NotDirectoryException(
                    "'" + file.getAbsolutePath().toString() + "' is not a directory.");
        }
        File attachedFile = new File(uploadDirectory, formatter.toString());
        boolean isMoved = file.renameTo(attachedFile);

        if(!isMoved){
            FileUtils.copyFile(file, attachedFile);
            file.delete();
        }

        // Close all resources.
        formatter.close();
        dis.close();

        return hash;
    }

    // Store the file as the name in the container.
    // Return true only if the attachment record is created because there was
    // no same record.
    public boolean store(File file, String name, Resource container) throws IOException, NoSuchAlgorithmException {
        // Store the file as its SHA1 hash in filesystem, and record its
        // metadata - projectId, containerType, containerId, size and hash - in Database.

        Project project = container.getProject();
        this.projectId = project == null ? 0L : project.id;
        this.containerType = container.getType();
        this.containerId = container.getId();

        if (name == null) {
            this.name = file.getName();
        } else {
            this.name = name;
        }

        if (this.mimeType == null) {
            this.mimeType = new Tika().detect(file);
        }

        // the size must be set before it is moved.
        this.size = file.length();
        this.hash = Attachment.moveFileIntoUploadDirectory(file);

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

    // Store the files in the user's area.
    public boolean storeToUserArea(File file, String name, Long userId)
            throws NoSuchAlgorithmException, IOException {
        return store(file, name, User.find.byId(userId).asResource());
    }

    public File getFile() {
        return new File(uploadDirectory, this.hash);
    }

    public static void setUploadDirectory(String path) {
        uploadDirectory = path;
    }

    public static boolean fileExists(String hash) {
        return new File(uploadDirectory, hash).isFile();
    }

    @Override
    public void delete() {
        super.delete();
        if (!exists(this.hash)) {
            new File(uploadDirectory, this.hash).delete();
        }
    }

    public static void deleteAll(ResourceType containerType, Long containerId) {
        List<Attachment> attachments = findByContainer(containerType, containerId);
        for (Attachment attachment : attachments) {
            attachment.delete();
        }
    }

    public static void deleteAll(Resource resource) {
        deleteAll(resource.getType(), resource.getId());
    }

    public Resource asResource() {
        return new Resource() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Project getProject() {
                if (projectId != null) {
                    return Project.find.byId(projectId);
                } else {
                    return null;
                }
            }

            @Override
            public ResourceType getType() {
                return ResourceType.ATTACHMENT;
            }

            @Override
            public Resource getContainer() {
                return new Resource() {

                    @Override
                    public Long getId() {
                        return containerId;
                    }

                    @Override
                    public Project getProject() {
                        if (projectId != null) {
                            return Project.find.byId(projectId);
                        } else {
                            return null;
                        }
                    }

                    @Override
                    public ResourceType getType() {
                        return containerType;
                    }
                };
            }
        };
    }
}
