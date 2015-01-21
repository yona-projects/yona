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
package controllers;

import controllers.annotation.AnonymousCheck;
import models.Attachment;
import models.User;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import play.Configuration;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import utils.AccessControl;
import utils.HttpUtil;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static play.libs.Json.toJson;

@AnonymousCheck
public class AttachmentApp extends Controller {

    public static final String TAG_NAME_FOR_TEMPORARY_UPLOAD_FILES = "temporaryUploadFiles";
    public static final long TEMPORARYFILES_KEEPUP_TIME_MILLIS = Configuration.root()
            .getMilliseconds("application.temporaryfiles.keep-up.time", 24 * 60 * 60 * 1000L);

    public static Result uploadFile() throws NoSuchAlgorithmException, IOException {
        // Get the file from request.
        FilePart filePart =
                request().body().asMultipartFormData().getFile("filePath");
        if (filePart == null) {
            return badRequest();
        }
        File file = filePart.getFile();

        User uploader = UserApp.currentUser();

        // Anonymous cannot upload a file.
        if (uploader.isAnonymous()) {
            return forbidden();
        }

        // Attach the file to the user who upload it.
        Attachment attach = new Attachment();
        boolean isCreated = attach.store(file, filePart.getFilename(), uploader.asResource());

        // The request has been fulfilled and resulted in a new resource being
        // created. The newly created resource can be referenced by the URI(s)
        // returned in the entity of the response, with the most specific URI
        // for the resource given by a Location header field.
        // -- RFC 2616, 10.2.2. 201 Created
        String url = routes.AttachmentApp.getFile(attach.id).url();
        response().setHeader("Location", url);

        // The entity format is specified by the media type given in the
        // Content-Type header field. -- RFC 2616, 10.2.2. 201 Created
        // While upload a file using Internet Explorer, if the response is not in
        // text/html, the browser will prompt the user to download it as a file.
        // To avoid this, if application/json is not acceptable by client, the
        // Content-Type field of response is set to "text/html". But, ACTUALLY
        // IT WILL BE SEND IN JSON!
        String contentType = HttpUtil.getPreferType(request(), "application/json", "text/html");
        response().setHeader("Content-Type", contentType);
        response().setHeader("Vary", "Accept");

        // The response SHOULD include an entity containing a list of resource
        // characteristics and location(s) from which the user or user agent can
        // choose the one most appropriate. -- RFC 2616, 10.2.2. 201 Created
        Map<String, String> fileInfo = new HashMap<>();
        fileInfo.put("id", attach.id.toString());
        fileInfo.put("mimeType", attach.mimeType);
        fileInfo.put("name", attach.name);
        fileInfo.put("url", url);
        fileInfo.put("size", attach.size.toString());
        JsonNode responseBody = toJson(fileInfo);

        if (isCreated) {
            // If an attachment has been created - it does NOT mean that
            // a file is created in the filesystem - return 201 Created.
            return created(responseBody);
        } else {
            // If the attachment already exists, return 200 OK.
            // Why not 204? Because 204 doesn't allow response to have a body,
            // so we cannot tell what is same with the file you try to add.
            return ok(responseBody);
        }
    }

    public static Result getFile(Long id) throws IOException {
        Attachment attachment = Attachment.find.byId(id);
        String action = HttpUtil.getFirstValueFromQuery(request().queryString(), "action");
        String dispositionType = StringUtils.equals(action, "download") ? "attachment" : "inline";

        if (attachment == null) {
            return notFound("The file does not exist.");
        }

        String eTag = "\"" + attachment.hash + "-" + dispositionType + "\"";

        if (!AccessControl.isAllowed(UserApp.currentUser(), attachment.asResource(), Operation.READ)) {
            return forbidden("You have no permission to get the file.");
        }

        response().setHeader("Cache-Control", "private, max-age=3600");

        String ifNoneMatchValue = request().getHeader("If-None-Match");
        if(ifNoneMatchValue != null && ifNoneMatchValue.equals(eTag)) {
            response().setHeader("ETag", eTag);
            return status(NOT_MODIFIED);
        }

        File file = attachment.getFile();

        if(file != null && !file.exists()){
            Logger.error("Attachment ID:" + id + " (" + file.getAbsolutePath() + ") does not exist on storage");
            return internalServerError("The file does not exist");
        }

        String filename = HttpUtil.encodeContentDisposition(attachment.name);

        response().setHeader("Content-Type", attachment.mimeType);
        response().setHeader("Content-Disposition", dispositionType + "; " + filename);
        response().setHeader("ETag", eTag);

        return ok(file);
    }

    public static Result deleteFile(Long id) {
        // _method must be 'delete'
        Map<String, String[]> data =
                request().body().asMultipartFormData().asFormUrlEncoded();
        if (!HttpUtil.getFirstValueFromQuery(data, "_method").toLowerCase()
                .equals("delete")) {
            return badRequest("_method must be 'delete'.");
        }

        // Remove the attachment.
        Attachment attach = Attachment.find.byId(id);
        if (attach == null) {
            return notFound();
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), attach.asResource(), Operation.DELETE)) {
            return forbidden();
        }

        attach.delete();

        logIfOriginFileIsNotValid(attach.hash);

        if (Attachment.fileExists(attach.hash)) {
            return ok("The attachment is removed successfully, but its origin file still exists.");
        } else {
            return ok("Both the attachment and its origin file are removed successfully.");
        }
    }

    private static void logIfOriginFileIsNotValid(String hash) {
        if (!Attachment.fileExists(hash) && Attachment.exists(hash)) {
            Logger.error("The origin file '" + hash + "' cannot be " +
                    "found even if the file is still referred by some" +
                    "attachments.");
        }

        if (Attachment.fileExists(hash) && !Attachment.exists(hash)) {
            Logger.warn("The attachment is removed successfully, but its " +
                    "origin file '" + hash + "' still exists abnormally even if the file " +
                    "referred by nowhere.");
        }
    }

    private static Map<String, String> extractFileMetaDataFromAttachementAsMap(Attachment attach) {
        Map<String, String> metadata = new HashMap<>();

        metadata.put("id", attach.id.toString());
        metadata.put("mimeType", attach.mimeType);
        metadata.put("name", attach.name);
        metadata.put("url", routes.AttachmentApp.getFile(attach.id).url());
        metadata.put("size", attach.size.toString());

        return metadata;
    }

    public static Map<String, List<Map<String, String>>> getFileList(String containerType, String
            containerId) throws PermissionDeniedException {
        Map<String, List<Map<String, String>>> files =
                new HashMap<>();

        if (StringUtils.isNotEmpty(containerType) && StringUtils.isNotEmpty(containerId)) {
            List<Map<String, String>> attachments = new ArrayList<>();
            for (Attachment attach : Attachment.findByContainer(ResourceType.valueOf
                    (containerType), containerId)) {
                if (!AccessControl.isAllowed(UserApp.currentUser(),
                        attach.asResource(), Operation.READ)) {
                    throw new PermissionDeniedException();
                }
                attachments.add(extractFileMetaDataFromAttachementAsMap(attach));
            }
            files.put("attachments", attachments);
        }

        return files;
    }

    public static Result getFileList() {
        // Get attached files only if the user has permission to read it.
        Map<String, String[]> query = request().queryString();
        String containerType = HttpUtil.getFirstValueFromQuery(query, "containerType");
        String containerId = HttpUtil.getFirstValueFromQuery(query, "containerId");

        // Return the list of files as JSON.
        response().setHeader("Content-Type", "application/json");
        try {
            return ok(toJson(getFileList(containerType, containerId)));
        } catch (PermissionDeniedException e) {
            return forbidden();
        }
    }

    private static class PermissionDeniedException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
