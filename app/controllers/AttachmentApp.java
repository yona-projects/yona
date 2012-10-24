package controllers;

import static play.libs.Json.toJson;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.internet.MimeUtility;

import models.Attachment;
import models.enumeration.Operation;
import models.enumeration.Resource;

import org.apache.tika.Tika;
import org.codehaus.jackson.JsonNode;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Http.Request;
import play.mvc.Result;
import utils.AccessControl;
import utils.RequestUtil;

public class AttachmentApp extends Controller {

    public static Result newFile() throws NoSuchAlgorithmException, IOException {
        FilePart filePart = request().body().asMultipartFormData().getFile("filePath");

        // Currently, anonymous cannot upload a file.
        if (UserApp.currentUser() == UserApp.anonymous) {
            return forbidden();
        }

        // Keep the file in the user's temporary area.
        Attachment attach = new Attachment();
        attach.projectId = 0L;
        attach.containerType = Resource.USER;
        attach.containerId = UserApp.currentUser().id;
        attach.mimeType = new Tika().detect(filePart.getFile());
        attach.name = filePart.getFilename();

        // Store the file in the filesystem.
        String hash = AttachmentApp.saveFile(request());
        attach.hash = hash;

        Attachment sameAttach = Attachment.findBy(attach);

        if (sameAttach == null) {
            attach.save();
        } else {
            attach = sameAttach;
        }

        // The request has been fulfilled and resulted in a new resource being
        // created. The newly created resource can be referenced by the URI(s)
        // returned in the entity of the response, with the most specific URI
        // for the resource given by a Location header field.
        //     -- RFC 2616, 10.2.2. 201 Created
        String url = routes.AttachmentApp.getFile(attach.id, URLEncoder.encode(attach.name, "UTF-8")).url();
        response().setHeader("Location", url);

        // The response SHOULD include an entity containing a list of resource
        // characteristics and location(s) from which the user or user agent can
        // choose the one most appropriate. -- RFC 2616, 10.2.2. 201 Created
        Map<String, String> file = new HashMap<String, String>();
        file.put("id", attach.id.toString());
        file.put("mimeType", attach.mimeType);
        file.put("name", attach.name);
        file.put("url", url);
        JsonNode responseBody = toJson(file);

        // The entity format is specified by the media type given in the
        // Content-Type header field. -- RFC 2616, 10.2.2. 201 Created
        response().setHeader("Content-Type", "application/json");

        if (sameAttach == null) {
            // If an attachment has been created -- it does NOT mean that
            // a file is created in the filesystem -- return 201 Created.
            return created(responseBody);
        } else {
            // If the attachment already exists, return 200 OK.
            // Why not 204? -- Because 204 doesn't allow that response has body,
            // we cannot tell what is same with the file you try to add.
            return ok(responseBody);
        }
    }

    public static Result getFile(Long id, String filename)
            throws NoSuchAlgorithmException, IOException {
        Attachment attachment = Attachment.findById(id);

        if (attachment == null) {
            return notFound();
        }

        if (!AccessControl.isAllowed(UserApp.currentUser().id, attachment.projectId, attachment.containerType, Operation.READ, attachment.containerId)) {
            return forbidden();
        }

        File file = new File("public/uploadFiles/" + attachment.hash);

        // RFC 2231; IE 8 or less, and Safari 5 or less are not supported.
        filename = attachment.name.replaceAll("[:\\x5c\\/{?]", "_");
        filename = "filename*=UTF-8''" + URLEncoder.encode(filename, "UTF-8");

        response().setHeader("Content-Length", Long.toString(file.length()));
        response().setHeader("Content-Type", attachment.mimeType);
        response().setHeader("Content-Disposition", "attachment; " + filename);

        return ok(file);
    }

    public static Result deleteFile(Long id, String filename) throws NoSuchAlgorithmException, IOException {
        // _method must be 'delete'
        Map<String, String[]> data = request().body().asMultipartFormData().asFormUrlEncoded();
        if (!RequestUtil.getFirstValueFromQuery(data, "_method").toLowerCase().equals("delete")) {
            return badRequest("_method must be 'delete'.");
        }

        // Remove the attachment.
        Attachment attach = Attachment.findById(id);
        if (attach == null) {
            return notFound();
        }
        attach.delete();

        // Delete the file matched with the attachment,
        // if and only if no attachment refer the file.
        if (Attachment.exists(attach.hash)) {
            return ok("The attachment is removed successfully, but the origin file still exists because it is referred by somewhere.");
        }

        boolean result = new File("public/uploadFiles/" + attach.hash).delete();

        if (result) {
            return ok("The both of attachment and origin file is removed successfully.");
        } else {
            return status(202, "The attachment is removed successfully, but the origin file still exists abnormally even if it is referred by nowhere.");
        }
    }

    /**
     * From BoardApp
     *
     * @param request
     * @return
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    static String saveFile(Request request) throws NoSuchAlgorithmException, IOException {
        MultipartFormData body = request.body().asMultipartFormData();
        FilePart filePart = body.getFile("filePath");

        if (filePart != null) {
            MessageDigest algorithm = MessageDigest.getInstance("SHA1");
            DigestInputStream dis = new DigestInputStream(new BufferedInputStream(new FileInputStream(filePart.getFile())), algorithm);
            while(dis.read() != -1);
            Formatter formatter = new Formatter();
            for (byte b : algorithm.digest()) {
                formatter.format("%02x", b);
            }
            File saveFile = new File("public/uploadFiles/" + formatter.toString());
            filePart.getFile().renameTo(saveFile);
            String hash = formatter.toString();

            formatter.close();
            dis.close();

            return hash;
        }
        return null;
    }

    public static Map<String, String> fileAsMap(Attachment attach) {
        Map<String, String> file = new HashMap<String, String>();

        file.put("id", attach.id.toString());
        file.put("mimeType", attach.mimeType);
        file.put("name", attach.name);
        file.put("url", routes.AttachmentApp.getFile(attach.id, attach.name).url());

        return file;
    }

    public static Result getFileList() {
        Map<String, List<Map<String, String>>> files = new HashMap<String, List<Map<String, String>>>();

        List<Map<String, String>> tempFiles = new ArrayList<Map<String, String>>();
        for (Attachment attach : Attachment.findTempFiles(UserApp.currentUser().id)) {
            tempFiles.add(fileAsMap(attach));
        }
        files.put("tempFiles", tempFiles);

        Map<String, String[]> query = request().queryString();
        String containerType = RequestUtil.getFirstValueFromQuery(query, "containerType");
        String containerId = RequestUtil.getFirstValueFromQuery(query, "containerId");

        if (containerType != null && containerId != null) {
            List<Map<String, String>> attachments = new ArrayList<Map<String, String>>();
            for (Attachment attach : Attachment.findByContainer(Resource.valueOf(containerType), Long.parseLong(containerId))) {
                if (!AccessControl.isAllowed(UserApp.currentUser().id, attach.projectId, attach.containerType, Operation.READ, attach.containerId)) {
                    return forbidden();
                }
                attachments.add(fileAsMap(attach));
            }
            files.put("attachments", attachments);
        }

        JsonNode responseBody = toJson(files);

        response().setHeader("Content-Type", "application/json");

        return ok(responseBody);
    }
}