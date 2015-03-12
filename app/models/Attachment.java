/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
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
package models;

import controllers.AttachmentApp;
import models.enumeration.ResourceType;
import models.resource.GlobalResource;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import org.apache.commons.io.FileUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MimeTypeException;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.libs.Akka;
import scala.concurrent.duration.Duration;
import utils.AttachmentCache;
import utils.Config;
import utils.FileUtil;
import utils.JodaDateUtil;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Entity
public class Attachment extends Model implements ResourceConvertible {
    private static final long serialVersionUID = 7856282252495067924L;
    public static final Finder<Long, Attachment> find = new Finder<>(Long.class, Attachment.class);
    public static final int NOTHING_TO_ATTACH = 0;
    private static String uploadDirectory = "uploads";
    @Id
    public Long id;

    @Constraints.Required
    public String name;

    @Constraints.Required
    public String hash;

    @Enumerated(EnumType.STRING)
    public ResourceType containerType;

    public String mimeType;
    public Long size;
    public String containerId;

    private Date createdDate;

    /**
     * Finds an attachment which matches the given one.
     *
     * Finds an attachment that matches {@link Attachment#name},
     * {@link Attachment#hash}, {@link Attachment#containerType} and
     * {@link Attachment#containerId} with the given one.
     *
     * @param attach
     * @return an attachment which matches up with the given one.
     */
    private static Attachment findBy(Attachment attach) {
        return find.where()
                .eq("name", attach.name)
                .eq("hash", attach.hash)
                .eq("containerType", attach.containerType)
                .eq("containerId", attach.containerId).findUnique();
    }

    /**
     * @param hash
     * @return true if an attachment which has the given hash exists
     */
    public static boolean exists(String hash) {
        return find.where().eq("hash", hash).findRowCount() > 0;
    }

    /**
     * Gets all attachments from a container.
     *
     * @param containerType  the resource type of the container
     * @param containerId    the resource id of the container
     * @return attachments of the container
     */
    public static List<Attachment> findByContainer(
            ResourceType containerType, String containerId) {
        List<Attachment> cachedData = AttachmentCache.get(containerType, containerId);
        if (cachedData != null) {
            return cachedData;
        }

        List<Attachment> list = find.where()
                .eq("containerType", containerType)
                .eq("containerId", containerId).findList();
        AttachmentCache.set(containerType.name() + containerId, list);
        return list;
    }

    /**
     * Gets all attachments from a container.
     *
     * @param container
     * @return attachments of the container
     */
    public static List<Attachment> findByContainer(Resource container) {
        List<Attachment> cachedData = AttachmentCache.get(container);
        if (cachedData != null) {
            return cachedData;
        }

        List<Attachment> list = findByContainer(container.getType(), container.getId());
        AttachmentCache.set(container, list);
        return list;
    }

    /**
     * @param container
     * @return the number of attachments in the container
     */
    public static int countByContainer(Resource container) {
        return find.where()
                .eq("containerType", container.getType())
                .eq("containerId", container.getId()).findRowCount();
    }

    /**
     * Moves all attachments from a container to another container.
     *
     * This method is used when move attachments which were attached to an user
     * temporary to a specific resource(issue, posting, ...).
     *
     * @param from  a container in which the attachment is currently stored
     * @param to    a container to which the attachment moved
     * @return      the number of attachments which was moved to another
     *              container
     */
    public static int moveAll(Resource from, Resource to) {
        List<Attachment> attachments = Attachment.findByContainer(from);
        for (Attachment attachment : attachments) {
            attachment.moveTo(to);
        }
        return attachments.size();
    }

    /**
     * Moves specified attachments from a container to another one.
     *
     * This method is used when move attachments which were attached to an user
     * temporary to a specific resource(issue, posting, ...).
     *
     * @param from             a container to which it was attached
     * @param to               a container to which it will be attached
     * @param selectedFileIds  IDs of attachments to be moved
     * @return the number of attachments which was moved to another container
     */
    public static int moveOnlySelected(Resource from, Resource to, String[] selectedFileIds) {
        if(selectedFileIds.length == 0){
            return NOTHING_TO_ATTACH;
        }
        List<Attachment> attachments = Attachment.find.where().idIn(Arrays.asList(selectedFileIds)).findList();
        for (Attachment attachment : attachments) {
            if(attachment.containerId.equals(from.getId())
                    && attachment.containerType == from.getType()){
                attachment.moveTo(to);
            }
        }
        return attachments.size();
    }

    /**
     * Moves this attachment to another resource.
     *
     * @param to  the destination
     */
    public void moveTo(Resource to) {
        containerType = to.getType();
        containerId = to.getId();
        update();
    }

    /**
     * Moves a file to the Upload Directory.
     *
     * This method is used to move a file stored in temporary directory by
     * PlayFramework to the Upload Directory managed by Yobi.
     *
     * @param file
     * @return SHA1 hash of the file
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    private static File moveFileIntoUploadDirectory(File file)
            throws NoSuchAlgorithmException, IOException {
        // Compute sha1 checksum.
        InputStream is = new FileInputStream(file);
        byte buf[] = new byte[10240];
        MessageDigest algorithm = MessageDigest.getInstance("SHA1");
        for (int readSize = 0; readSize >= 0; readSize = is.read(buf)) {
            algorithm.update(buf, 0, readSize);
        }
        is.close();
        String hash = toHex(algorithm.digest());

        return moveFileIntoUploadDirectory(file, hash);
    }

    private static File moveFileIntoUploadDirectory(File file, String hash)
            throws NoSuchAlgorithmException, IOException {
        // Store the file.
        File attachedFile = new File(createUploadDirectory(), hash);
        boolean isMoved = file.renameTo(attachedFile);

        if(!isMoved){
            FileUtils.copyFile(file, attachedFile);
            file.delete();
        }

        return attachedFile;
    }

    /**
     * Attaches an uploaded file to the given container with the given name.
     *
     * Moves an uploaded file to the Upload Directory and rename the file to
     * its SHA1 hash. And it stores the metadata of the file in this entity.
     *
     * If there is an entity that has the same values with this entity already,
     * it means the container has the same attachment. If that is the case,
     * this method will return {@code false} and do nothing; otherwise, return
     * {@code true}.
     *
     * This method is used when an uploaded file is attached to a user or
     * another resource directly.
     *
     * @param file       a file to be attached
     * @param name       the name of the file
     * @param container  the resource to which the file attached
     * @return {@code true} if the file is attached, {@code false} otherwise.
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    @Transient
    public boolean store(File file, String name, Resource container) throws IOException, NoSuchAlgorithmException {
        return save(moveFileIntoUploadDirectory(file), name, container);
    }

    /**
     * Gets a file which mathces the hash from the Upload Directory.
     *
     * This method is used when an user downloads a file
     *
     * @return the file
     */
    public File getFile() {
        return new File(getUploadDirectory(), this.hash);
    }

    public static File getUploadDirectory() {
        return new File(utils.Config.getYobiHome(), uploadDirectory);
    }

    /**
     * Sets the Upload Directory to store files that users uploaded.
     *
     * This method is used for unit tests.
     *
     * @param path  a path to the Upload Directory
     */
    public static void setUploadDirectory(String path) {
        uploadDirectory = path;
    }

    /**
     * Checks if there is a file that has the same hash in the Upload Directory.
     *
     * This method is used to check if the file exists in the system.
     *
     * @param hash
     * @return true if the file exists
     */
    public static boolean fileExists(String hash) {
        return new File(getUploadDirectory(), hash).isFile();
    }

    /**
     * Deletes this file and remove cache that contains it.
     * However, the cache can not be removed if Ebean.delete() is directly used or called by cascading.
     *
     * This method is used when an user delete an attachment or its container.
     */
    @Override
    public void delete() {
        super.delete();
        // FIXME: Rarely this may delete a file which is still referred by
        // attachment, if new attachment is added after checking nonexistence
        // of an attachment refers the file and before deleting the file.
        //
        // But synchronization with Attachment class may be a bad idea to solve
        // the problem. If you do that, blocking of project deletion causes
        // that all requests to attachments (even a user avatars you can see in
        // most of pages) are blocked.
        if (!exists(this.hash)) {
            try {
                Files.delete(Paths.get(uploadDirectory, this.hash));
            } catch (Exception e) {
                play.Logger.error("Failed to delete: " + this, e);
            }
        }

        AttachmentCache.remove(this);
    }

    /**
     * Update this file and remove cache that contains it.
     * However, the cache can not be removed if Ebean.update() is directly used or called by cascading.
     */
    @Override
    public void update() {
        super.update();
        AttachmentCache.remove(this);
    }


    /**
     * Deletes every attachment attached to the given container.
     *
     * This method is used when a container, a resource may has attachments, is
     * deleted.
     *
     * @param container  the resource that has the attachments to be deleted
     */
    public static void deleteAll(Resource container) {
        List<Attachment> attachments = findByContainer(container);
        for (Attachment attachment : attachments) {
            attachment.delete();
        }
    }

    private String messageForLosingProject() {
        return "An attachment '" + this +"' lost the project it belongs to";
    }

    /**
     * Returns this as a resource.
     *
     * This method is used for access control.
     *
     * @return resource
     */
    @Override
    public Resource asResource() {
        boolean isContainerProject = containerType.equals(ResourceType.PROJECT);
        final Project project;
        final Resource container;

        if (isContainerProject) {
            project = Project.find.byId(Long.parseLong(containerId));
            if (project == null) {
                throw new RuntimeException(messageForLosingProject());
            }
            container = project.asResource();
        } else {
            container = Resource.get(containerType, containerId);
            if (!(container instanceof GlobalResource)) {
                project = container.getProject();
                if (project == null) {
                    throw new RuntimeException(messageForLosingProject());
                }
            } else {
                project = null;
            }
        }

        if (project != null) {
            return new Resource() {
                @Override
                public String getId() {
                    return id.toString();
                }

                @Override
                public Project getProject() {
                    return project;
                }

                @Override
                public ResourceType getType() {
                    return ResourceType.ATTACHMENT;
                }

                @Override
                public Resource getContainer() {
                    return container;
                }
            };
        } else {
            return new GlobalResource() {
                @Override
                public String getId() {
                    return id.toString();
                }

                @Override
                public ResourceType getType() {
                    return ResourceType.ATTACHMENT;
                }

                @Override
                public Resource getContainer() {
                    return container;
                }
            };
        }
    }

    /**
     * Remove all of temporary files uploaded by users
     */
    private static void cleanupTemporaryUploadFilesWithSchedule() {
        Akka.system().scheduler().schedule(
                Duration.create(0, TimeUnit.SECONDS),
                Duration.create(AttachmentApp.TEMPORARYFILES_KEEPUP_TIME_MILLIS, TimeUnit.MILLISECONDS),
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String result = removeUserTemporaryFiles();
                            play.Logger.info("User uploaded temporary files are cleaned up..." + result);
                        } catch (Exception e) {
                            play.Logger.warn("Failed!! User uploaded temporary files clean-up action failed!", e);
                        }
                    }

                    private String removeUserTemporaryFiles() {
                        List<Attachment> attachmentList = Attachment.find.where()
                                .eq("containerType", ResourceType.USER)
                                .ge("createdDate", JodaDateUtil.beforeByMillis(AttachmentApp.TEMPORARYFILES_KEEPUP_TIME_MILLIS))
                                .findList();
                        int deletedFileCount = 0;
                        for (Attachment attachment : attachmentList) {
                            attachment.delete();
                            deletedFileCount++;
                        }
                        if (attachmentList.size() != deletedFileCount) {
                            play.Logger.error(
                                    String.format("Failed to delete user temporary files.\nExpected: %d  Actual: %d",
                                            attachmentList.size(), deletedFileCount)
                            );
                        }
                        return String.format("(%d of %d)", attachmentList.size(), deletedFileCount);
                    }
                },
                Akka.system().dispatcher()
        );
    }

    public static void onStart() {
        cleanupTemporaryUploadFilesWithSchedule();
    }

    @Override
    public String toString() {
        return "Attachment{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", hash='" + hash + '\'' +
                ", containerType=" + containerType +
                ", mimeType='" + mimeType + '\'' +
                ", size=" + size +
                ", containerId='" + containerId + '\'' +
                ", createdDate=" + createdDate +
                '}';
    }

    public boolean store(InputStream inputStream, @Nullable String fileName,
                         Resource container) throws
            IOException, NoSuchAlgorithmException {
        byte buf[] = new byte[10240];

        // Compute hash and store the stream as a temp file
        MessageDigest algorithm = MessageDigest.getInstance("SHA1");
        String tempFileHash;
        File tmpFile = File.createTempFile("yobi", null);
        FileOutputStream fos = new FileOutputStream(tmpFile);
        try {
            int readSize;
            while ((readSize = inputStream.read(buf)) != -1) {
                algorithm.update(buf, 0, readSize);
                fos.write(buf, 0, readSize);
            }
            tempFileHash = toHex(algorithm.digest());
            fos.flush();
        } finally {
            fos.close();
        }

        // Save this attachment with metadata
        return save(moveFileIntoUploadDirectory(tmpFile, tempFileHash), fileName, container);
    }

    /**
     * Save this attachment with metadata from the given arguments.
     *
     * @param file       the file to be attached whose name is hash of the contents
     * @param fileName   the name of this attachment
     * @param container  the container to which the file attached
     * @return
     * @throws IOException
     */
    private boolean save(File file, String fileName, Resource container) throws
            IOException {
        // Store the file as its SHA1 hash in filesystem, and record its
        // metadata - containerType, containerId, createdDate, name, size, hash and
        // mimeType - in Database.
        this.containerType = container.getType();
        this.containerId = container.getId();
        this.createdDate = JodaDateUtil.now();
        this.hash = file.getName();
        this.size = file.length();
        if (this.mimeType == null) {
            this.mimeType = FileUtil.detectMediaType(file, name).toString();
        }
        if (fileName == null) {
            this.name = String.valueOf(new Date().getTime());
            try {
                this.name += "." + TikaConfig.getDefaultConfig()
                        .getMimeRepository().forName(this.mimeType).getExtension();
            } catch (MimeTypeException e) {
            }
        } else {
            this.name = fileName;
        }

        AttachmentCache.remove(this);

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

    private static String toHex(byte[] bytes) {
        Formatter formatter = new Formatter();
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }
        String hex = formatter.toString();
        formatter.close();
        return hex;
    }

    // Create the upload directory if it doesn't exist.
    private static File createUploadDirectory() throws NotDirectoryException {
        File uploads = getUploadDirectory();
        uploads.mkdirs();
        if (!uploads.isDirectory()) {
            throw new NotDirectoryException(
                    "'" + uploads.getAbsolutePath() + "' is not a directory.");
        }
        return uploads;
    }

}
