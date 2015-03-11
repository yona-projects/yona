/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
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
import models.*;
import models.enumeration.Direction;
import models.enumeration.Operation;
import models.resource.Resource;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import play.data.Form;
import play.db.ebean.Model;
import play.i18n.Messages;
import play.mvc.*;
import utils.*;

@AnonymousCheck
public class AbstractPostingApp extends Controller {
    public static final int ITEMS_PER_PAGE = 15;

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
        preUpdateHook.run();

        try {
            posting.checkLabels();
        } catch (IssueLabel.IssueLabelException e) {
            return badRequest(e.getMessage());
        }

        posting.update();
        posting.updateProperties();

        // Attach the files in the current user's temporary storage.
        attachUploadFilesToPost(original.asResource());

        return redirect(redirectTo);
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

    private static long getTemporaryFilesServerKeepUpTimeOfMinuntes() {
        return AttachmentApp.TEMPORARYFILES_KEEPUP_TIME_MILLIS/(60*1000l);
    }

    private static String[] getTemporaryFileListFromHiddenForm() {
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
}
