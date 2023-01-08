/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.annotation.AnonymousCheck;
import models.*;
import models.enumeration.Direction;
import models.enumeration.Operation;
import models.resource.Resource;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import play.data.Form;
import play.db.ebean.Model;
import play.i18n.Messages;
import play.mvc.Call;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.*;

import java.util.LinkedList;
import java.util.Map;

import static utils.JodaDateUtil.getDateString;
import static utils.diff_match_patch.Diff;

@AnonymousCheck
public class AbstractPostingApp extends Controller {
    public static final int ITEMS_PER_PAGE = 15;
    private static final short Diff_EditCost = 16;

    public static class SearchCondition {
        public String orderBy;
        public String orderDir;
        public String filter;
        public int pageNum;

        public SearchCondition() {
            this.orderDir = Direction.DESC.direction();
            this.orderBy = "id";
            this.filter = "";
            this.pageNum = 1;
        }
    }

    public static Comment saveComment(final Comment comment, Runnable containerUpdater) {
        containerUpdater.run(); // this updates comment.issue or comment.posting;

        if(comment.id != null && AccessControl.isAllowed(UserApp.currentUser(), comment.asResource(), Operation.UPDATE)) {
            comment.update();
        } else {
            comment.setAuthor(UserApp.currentUser());
            comment.save();
        }

        // Attach all of the files in the current user's temporary storage.
        attachUploadFilesToPost(comment.asResource());

        return comment;
    }

    protected static Result delete(Model target, Resource resource, Call redirectTo) {
        if (!AccessControl.isAllowed(UserApp.currentUser(), resource, Operation.DELETE)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", resource.getProject()));
        }

        target.delete();

        if(HttpUtil.isRequestedWithXHR(request())){
            response().setHeader("Location", redirectTo.url());
            return status(204);
        }

        return redirect(redirectTo);
    }

    protected static Result editPosting(AbstractPosting original, AbstractPosting posting, Form<? extends AbstractPosting> postingForm, Call redirectTo, Runnable preUpdateHook) {
        if (postingForm.hasErrors()) {
            return badRequest(ErrorViews.BadRequest.render("error.validation", original.project));
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), original.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", original.project));
        }

        if (posting.body == null) {
            return status(REQUEST_ENTITY_TOO_LARGE,
                    ErrorViews.RequestTextEntityTooLarge.render());
        }

        posting.id = original.id;
        posting.createdDate = original.createdDate;
        posting.updatedDate = JodaDateUtil.now();
        posting.authorId = original.authorId;
        posting.authorLoginId = original.authorLoginId;
        posting.authorName = original.authorName;
        posting.project = original.project;
        posting.setNumber(original.getNumber());
        posting.updatedByAuthorId = UserApp.currentUser().id;
        if (posting.isPublish) {
            posting.history = "";
        } else {
            if (!StringUtils.defaultString(original.body, "").equals(StringUtils.defaultString(posting.body, ""))) {
                posting.history = addToHistory(original, posting) + StringUtils.defaultString(original.history, "");
            }
        }
        preUpdateHook.run();

        try {
            posting.checkLabels();
        } catch (IssueLabel.IssueLabelException e) {
            return badRequest(e.getMessage());
        }

        posting.update();
        posting.updateProperties();

        TitleHead.saveTitleHeadKeyword(posting.project, posting.title);
        TitleHead.deleteTitleHeadKeyword(original.project, original.title);

        // Attach the files in the current user's temporary storage.
        attachUploadFilesToPost(original.asResource());

        return redirect(redirectTo);
    }

    private static String addToHistory(AbstractPosting original, AbstractPosting posting) {
        diff_match_patch dmp = new diff_match_patch();
        dmp.Diff_EditCost = Diff_EditCost;
        LinkedList<diff_match_patch.Diff> diffs = dmp.diff_main(original.body, posting.body);
        dmp.diff_cleanupEfficiency(diffs);

        return (getHistoryMadeBy(posting, diffs) + getDiffText(original.body, posting.body) + "\n").replaceAll("\n", "</br>\n");
    }

    private static String getHistoryMadeBy(AbstractPosting posting, LinkedList<diff_match_patch.Diff> diffs) {
        int insertions = 0;
        int deletions = 0;
        for (Diff diff : diffs) {
            switch (diff.operation) {
                case DELETE:
                    deletions++;
                    break;
                case INSERT:
                    insertions++;
                    break;
                default:
                    break;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='history-made-by'>").append(UserApp.currentUser().name)
                .append("(").append(UserApp.currentUser().loginId).append(") ");
        if (insertions > 0) {
            sb.append("<span class='added'> ")
                    .append(" + ")
                    .append(insertions).append(" </span>");
        }
        if (deletions > 0) {
            sb.append("<span class='deleted'> ")
                    .append(" - ")
                    .append(deletions).append(" </span>");
        }
        sb.append(" at ").append(getDateString(posting.updatedDate, "yyyy-MM-dd h:mm:ss a")).append("</div><hr/>\n");

        return sb.toString();
    }

    private static String getDiffText(String oldValue, String newValue) {
        final int EQUAL_TEXT_ELLIPSIS_SIZE = 100;
        diff_match_patch dmp = new diff_match_patch();
        dmp.Diff_EditCost = Diff_EditCost;
        StringBuilder sb = new StringBuilder();
        if (oldValue != null) {
            LinkedList<diff_match_patch.Diff> diffs = dmp.diff_main(oldValue, newValue);
            dmp.diff_cleanupEfficiency(diffs);
            for(Diff diff: diffs){
                switch (diff.operation) {
                    case DELETE:
                        sb.append("<span class='diff-deleted'>");
                        sb.append(StringEscapeUtils.escapeHtml4(diff.text));
                        sb.append("</span>");
                        break;
                    case EQUAL:
                        int textLength = diff.text.length();
                        if(textLength > EQUAL_TEXT_ELLIPSIS_SIZE) {
                            if(!diff.text.substring(0, 50).equals(oldValue.substring(0, 50))) {
                                sb.append(StringEscapeUtils.escapeHtml4(diff.text.substring(0, 50)));
                            }
                            sb.append("<span class='diff-ellipsis'>...\n")
                                    .append("......\n")
                                    .append("......\n")
                                    .append("...</span>");
                            if(!diff.text.substring(textLength - 50).equals(oldValue.substring(oldValue.length() - 50))) {
                                    sb.append(StringEscapeUtils.escapeHtml4(diff.text.substring(textLength - 50)));
                            }
                        } else {
                            sb.append(StringEscapeUtils.escapeHtml4(diff.text));
                        }
                        break;
                    case INSERT:
                        sb.append("<span class='diff-added'>");
                        sb.append(StringEscapeUtils.escapeHtml4(diff.text));
                        sb.append("</span>");
                        break;
                    default:
                        break;
                }
            }
        }
        return sb.toString().replaceAll("\n", "&nbsp<br/>\n");
    }

    public static void attachUploadFilesToPost(Resource resource) {
        final String[] temporaryUploadFiles = getTemporaryFileListFromHiddenForm();
        if(isTemporaryFilesExist(temporaryUploadFiles)){
            int attachedFileCount = Attachment.moveOnlySelected(UserApp.currentUser().asResource(), resource,
                    temporaryUploadFiles);
            if( attachedFileCount != temporaryUploadFiles.length){
                flash(Constants.TITLE, Messages.get("post.popup.fileAttach.hasMissing", temporaryUploadFiles.length - attachedFileCount));
                flash(Constants.DESCRIPTION, Messages.get("post.popup.fileAttach.hasMissing.description", getTemporaryFilesServerKeepUpTimeOfMinuntes()));
            }
        }
    }

    public static void attachUploadFilesToPost(JsonNode files, Resource resource) {
        if(files != null && files.isArray() && files.size() > 0){
            String [] fileIds = new String[files.size()];
            int idx = 0;
            for (JsonNode fileNo : files) {
                fileIds[idx] = fileNo.asText();
                idx++;
            }
            int attachedFileCount = Attachment.moveOnlySelected(UserApp.currentUser().asResource(), resource,
                    fileIds);
            if( attachedFileCount != files.size()){
                flash(Constants.TITLE, Messages.get("post.popup.fileAttach.hasMissing", files.size() - attachedFileCount));
                flash(Constants.DESCRIPTION, Messages.get("post.popup.fileAttach.hasMissing.description", getTemporaryFilesServerKeepUpTimeOfMinuntes()));
            }
        }
    }

    private static long getTemporaryFilesServerKeepUpTimeOfMinuntes() {
        return AttachmentApp.TEMPORARYFILES_KEEPUP_TIME_MILLIS/(60*1000l);
    }

    public static String[] getTemporaryFileListFromHiddenForm() {
        Http.MultipartFormData body = request().body().asMultipartFormData();
        if (body == null) {
            return new String[] {};
        }
        String [] temporaryUploadFiles = body.asFormUrlEncoded().get(AttachmentApp.TAG_NAME_FOR_TEMPORARY_UPLOAD_FILES);
        if (temporaryUploadFiles == null) {
            return new String[] {};
        }
        final String CSV_DELIMITER = ",";
        return temporaryUploadFiles[0].split(CSV_DELIMITER);
    }

    private static boolean isTemporaryFilesExist(String[] files) {
        if (ArrayUtils.getLength(files) == 0) {
            return false;
        }
        return StringUtils.isNotBlank(files[0]);
    }

    protected static boolean isSelectedToSendNotificationMail() {
        Map<String,String[]> data;
        if (isMultiPartFormData()) {
            data = request().body().asMultipartFormData().asFormUrlEncoded();
        } else {
            data = request().body().asFormUrlEncoded();
        }
        return "yes".equalsIgnoreCase(HttpUtil.getFirstValueFromQuery(data, "notificationMail"));
    }

    private static boolean isMultiPartFormData() {
        return request().body().asMultipartFormData() != null;
    }
}
