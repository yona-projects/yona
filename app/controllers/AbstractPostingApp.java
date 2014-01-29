package controllers;

import models.*;
import models.enumeration.Direction;
import models.enumeration.Operation;
import models.resource.Resource;

import org.apache.commons.lang3.StringUtils;
import play.data.Form;
import play.db.ebean.Model;
import play.mvc.Call;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.*;

import java.io.IOException;

/**
 * {@link BoardApp}과 {@link IssueApp}에서 공통으로 사용하는 기능을 담고 있는 컨트롤러 클래스
 */
public class AbstractPostingApp extends Controller {
    public static final int ITEMS_PER_PAGE = 15;

    /**
     * 검색 조건
     */
    public static class SearchCondition {
        public String orderBy;
        public String orderDir;
        public String filter;
        public int pageNum;

        /**
         * 기본 검색 조건으로 id 역순이며 1페이지를 보여준다.
         */
        public SearchCondition() {
            this.orderDir = Direction.DESC.direction();
            this.orderBy = "id";
            this.filter = "";
            this.pageNum = 1;
        }
    }

    /**
     * 새 댓글 저장 핸들러
     *
     * {@code commentForm}에서 입력값을 꺼내 현재 사용자를 작성자로 설정하고 댓글을 저장한다.
     * 현재 사용자 임시 저장소에 있는 첨부파일을 댓글의 첨부파일로 옮긴다.
     *
     *
     * @param comment
     * @param commentForm
     * @param toView
     * @param containerUpdater
     * @return
     * @throws IOException
     */
    public static Result newComment(final Comment comment, Form<? extends Comment> commentForm, final Call toView, Runnable containerUpdater) {
        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "post.comment.empty");
            return redirect(toView);
        }

        comment.setAuthor(UserApp.currentUser());
        containerUpdater.run(); // this updates comment.issue or comment.posting;
        comment.save();

        // Attach all of the files in the current user's temporary storage.
        attachUploadFilesToPost(comment.asResource());

        String urlToView = toView + "#comment-" + comment.id;
        NotificationEvent.afterNewComment(comment, urlToView);
        return redirect(urlToView);
    }


    /**
     * {@code target}을 삭제하고 {@code redirectTo}로 이동한다.
     *
     * when: 게시물이나 이슈 또는 그곳에 달린 댓글을 삭제할 때 사용한다.
     *
     * @param target
     * @param resource
     * @param redirectTo
     * @return
     */
    protected static Result delete(Model target, Resource resource, Call redirectTo) {
        if (!AccessControl.isAllowed(UserApp.currentUser(), resource, Operation.DELETE)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", resource.getProject()));
        }

        target.delete();

        // XHR 호출에 의한 경우라면 204 No Content 와 Location 헤더로 응답한다
        if(HttpUtil.isRequestedWithXHR(request())){
            response().setHeader("Location", redirectTo.absoluteURL(request()));
            return status(204);
        }

        return redirect(redirectTo);
    }

    /**
     * {@code posting}에 {@code original} 정보를 채우고 갱신한다.
     *
     * when: 게시물이나 이슈를 수정할 떄 사용한다.
     *
     * 게시물이나 이슈가 수정될 때 {@code noti} 객체가 null이 아니면 알림을 발송한다.
     *
     *
     *
     * @param original
     * @param posting
     * @param postingForm
     * @param redirectTo
     * @param updatePosting
     * @return
     */
    protected static Result editPosting(AbstractPosting original, AbstractPosting posting, Form<? extends AbstractPosting> postingForm, Call redirectTo, Runnable updatePosting) {
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
        updatePosting.run();
        posting.update();
        posting.updateProperties();

        // Attach the files in the current user's temporary storage.
        attachUploadFilesToPost(original.asResource());

        return redirect(redirectTo);
    }

    /**
     * 특정 리소스(게시글이나 댓글)에 사용자가 이전 폼에서 업로드한 임시파일을 첨부시킨다
     *
     * when: 이슈등록/수정, 게시판에 글 등록/수정, 댓글쓰기 등에서 업로드한 파일을 해당 리소스에 연결할 때
     *
     * {code AttachmentApp.TAG_NAME_FOR_TEMPORARY_UPLOAD_FILES}에 지정된 이름의 input tag에
     * 업로드한 임시파일의 file id 값이 ,(comma) separator로 구분되어 들어가 있다.
     *
     * @param resource 이슈글,게시판글,댓글
     */
    protected static void attachUploadFilesToPost(Resource resource) {
        Http.MultipartFormData body = request().body().asMultipartFormData();
        final String temporaryUploadFiles = body.asFormUrlEncoded().get(AttachmentApp.TAG_NAME_FOR_TEMPORARY_UPLOAD_FILES)[0];
        if(StringUtils.isNotBlank(temporaryUploadFiles)){
            Attachment.moveOnlySelected(UserApp.currentUser().asResource(), resource,
                    temporaryUploadFiles.split(","));
        }
    }
}
