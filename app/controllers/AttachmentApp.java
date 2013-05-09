package controllers;

import static play.libs.Json.toJson;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.Attachment;
import models.enumeration.Operation;
import models.enumeration.ResourceType;

import org.codehaus.jackson.JsonNode;

import play.Logger;
import play.api.http.MediaRange;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import utils.AccessControl;
import utils.HttpUtil;

public class AttachmentApp extends Controller {

    /**
     * 사용자 첨부파일로 업로드한다
     *
     * when 이슈나 글, 코멘트등에서 파일을 첨부하기 전에 먼저 업로드
     *
     * 멀티파트 폼데이터로 파일 업로드 요청을 받아서 서버에 파일 저장을 시도하고
     * 만약 이미 같은 파일이 서버내에 globally 존재한다면 200OK로 응답
     * 존재하지 않는 파일이라면 201 created로 응답
     *
     * 요청에 첨부파일이 없는 것으로 보일때는 400 Bad Request로 응답
     * 업로더가 익명 사용자일 경우에는 403 Forbidden 으로 응답
     *
     * @return 생성된 파일의 메타데이터를 JSON 타입으로 반환하는 응답
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public static Result uploadFile() throws NoSuchAlgorithmException, IOException {
        // Get the file from request.
        FilePart filePart =
                request().body().asMultipartFormData().getFile("filePath");
        if (filePart == null) {
            return badRequest();
        }
        File file = filePart.getFile();

        // Anonymous cannot upload a file.
        if (UserApp.currentUser() == UserApp.anonymous) {
            return forbidden();
        }

        // Store the file in the user's temporary area.
        Attachment attach = new Attachment();
        boolean isCreated = attach.storeToUserArea(file, filePart.getFilename(), UserApp.currentUser().id);

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
        List<MediaRange> accepts = request().acceptedTypes();
        String contentType = request().accepts("application/json") ? "application/json" : "text/html";
        response().setHeader("Content-Type", contentType);

        // The response SHOULD include an entity containing a list of resource
        // characteristics and location(s) from which the user or user agent can
        // choose the one most appropriate. -- RFC 2616, 10.2.2. 201 Created
        Map<String, String> fileInfo = new HashMap<String, String>();
        fileInfo.put("id", attach.id.toString());
        fileInfo.put("mimeType", attach.mimeType);
        fileInfo.put("name", attach.name);
        fileInfo.put("url", url);
        fileInfo.put("size", attach.size.toString());
        JsonNode responseBody = toJson(fileInfo);

        if (isCreated) {
            // If an attachment has been created -- it does NOT mean that
            // a file is created in the filesystem -- return 201 Created.
            return created(responseBody);
        } else {
            // If the attachment already exists, return 200 OK.
            // Why not 204? -- Because 204 doesn't allow that response has body,
            // so we cannot tell what is same with the file you try to add.
            return ok(responseBody);
        }
    }

    /**
     * {@code id}로 파일을 찾아서 첨부파일로 돌려준다.
     *
     * when: 첨부파일을 다운로드 받을 때
     *
     * 주의사항: 파일명이 깨지지 않도록 {@link utils.HttpUtil#encodeContentDisposition)}로 인코딩한다.
     *
     * @param id 첨부파일 id
     * @return 파일이 첨부된 응답
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public static Result getFile(Long id) throws NoSuchAlgorithmException, IOException {
        Attachment attachment = Attachment.findById(id);

        if (attachment == null) {
            return notFound();
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), attachment.asResource(), Operation.READ)) {
            return forbidden();
        }

        File file = attachment.getFile();

        String filename = HttpUtil.encodeContentDisposition(attachment.name);

        response().setHeader("Content-Type", attachment.mimeType);
        response().setHeader("Content-Disposition", "attachment; " + filename);

        return ok(file);
    }

    /**
     * {@code id}에 해당하는 첨부파일을 지운다.
     *
     * 게시물, 이슈, 댓글들의 첨부파일을 지울때 사용한다.
     *
     * 폼의 필드에 {@code _method}가 존재하고 값이 delete로 지정되어 있지 않으면 Bad Request로 응답한다.
     * 파일을 못 찾으면 Not Found
     * 삭제 권한이 없으면 Forbidden
     *
     * 첨부내용을 삭제한 후 해당 첨부의 origin 파일 유효검증
     *
     * @param id 첨부파일 id
     * @return attachment 삭제 결과 (하지만 해당 메시지를 쓰고 있지는 않다. 아까운 네크워크 자원..)
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public static Result deleteFile(Long id)
            throws NoSuchAlgorithmException, IOException {
        // _method must be 'delete'
        Map<String, String[]> data =
                request().body().asMultipartFormData().asFormUrlEncoded();
        if (!HttpUtil.getFirstValueFromQuery(data, "_method").toLowerCase()
                .equals("delete")) {
            return badRequest("_method must be 'delete'.");
        }

        // Remove the attachment.
        Attachment attach = Attachment.findById(id);
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

    /**
     * origin file의 유효성을 검증하고, 유효하지 않다면 로그를 남긴다.
     *
     * origin file이 존재하지 않지만 그 파일을 참조하는 첨부가 존재하는 경우엔 에러 로그를 남긴다.
     * origin file이 존재하지만 그 파일을 참조하는 첨부가 존재하지 않는 경우엔 경고 로그를 남긴다.
     *
     * @param hash origin file의 hash
     */
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

    /**
     * 첨부파일의 메타데이터를 가져온다.
     *
     * @param attach 첨부
     * @return 메타데이터를 맵으로
     */
    private static Map<String, String> extractFileMetaDataFromAttachementAsMap(Attachment attach) {
        Map<String, String> metadata = new HashMap<String, String>();

        metadata.put("id", attach.id.toString());
        metadata.put("mimeType", attach.mimeType);
        metadata.put("name", attach.name);
        metadata.put("url", routes.AttachmentApp.getFile(attach.id).url());
        metadata.put("size", attach.size.toString());

        return metadata;
    }

    /**
     * 파일의 목록을 가져온다.
     *
     * 이슈, 게시물, 댓글을 볼 때, 첨부된 파일들의 목록을 보여주기 위해
     * 이슈, 게시물, 댓글을 편집할 때, 첨부된 파일들의 목록 및 사용자 파일들의 목록을 보여주기 위해
     *
     * 로그인한 사용자의 파일들의 목록을 {@code tempFiles} 프로퍼티로 넘겨준다.
     * 첨부 파일들의 목록을 {@code attachments} 프로퍼티로 넘겨준다.
     * 첨부 파일들 중 로그인한 사용자가 읽기 권한을 갖지 못한 것이 하나라도 있다면 403 Forbidden 으로 응답한다.
     *
     * @return json 포맷으로 된 파일 목록을 본문으로 하는 응답. 다음고 같은 형식이다.
     *         {@code {tempFiles: 사용자 파일 목록, attachments: 첨부 파일 목록 }}
     */
    public static Result getFileList() {
        Map<String, List<Map<String, String>>> files =
                new HashMap<String, List<Map<String, String>>>();

        // Get files from the user's area.
        List<Map<String, String>> userFiles = new ArrayList<Map<String, String>>();
        for (Attachment attach : Attachment.findByContainer(UserApp.currentUser().asResource())) {
            userFiles.add(extractFileMetaDataFromAttachementAsMap(attach));
        }
        files.put("tempFiles", userFiles);

        // Get attached files only if the user has permission to read it.
        Map<String, String[]> query = request().queryString();
        String containerType = HttpUtil.getFirstValueFromQuery(query, "containerType");
        String containerId = HttpUtil.getFirstValueFromQuery(query, "containerId");

        if (containerType != null && containerId != null) {
            List<Map<String, String>> attachments = new ArrayList<Map<String, String>>();
            for (Attachment attach : Attachment.findByContainer(ResourceType.valueOf(containerType),
                    Long.parseLong(containerId))) {
                if (!AccessControl.isAllowed(UserApp.currentUser(),
                        attach.asResource(), Operation.READ)) {
                    return forbidden();
                }
                attachments.add(extractFileMetaDataFromAttachementAsMap(attach));
            }
            files.put("attachments", attachments);
        }

        // Return the list of files as JSON.
        response().setHeader("Content-Type", "application/json");
        return ok(toJson(files));
    }
}
