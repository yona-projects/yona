
package controllers;

import actions.AnonymousCheckAction;
import actions.NullProjectCheckAction;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;

import controllers.annotation.IsAllowed;
import controllers.annotation.IsCreatable;
import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;

import org.codehaus.jackson.node.ObjectNode;

import play.data.Form;
import play.db.ebean.Transactional;
import play.libs.Json;
import play.mvc.Call;
import play.mvc.Result;
import play.mvc.With;
import utils.AccessControl;
import utils.ErrorViews;
import utils.JodaDateUtil;
import views.html.board.create;
import views.html.board.edit;
import views.html.board.list;
import views.html.board.view;

import java.io.IOException;
import java.util.List;

import static com.avaje.ebean.Expr.icontains;

public class BoardApp extends AbstractPostingApp {
    public static class SearchCondition extends AbstractPostingApp.SearchCondition {
        private ExpressionList<Posting> asExpressionList(Project project) {
            ExpressionList<Posting> el = Posting.finder.where().eq("project.id", project.id);

            if (filter != null) {
                el.or(icontains("title", filter), icontains("body", filter));
            }

            if (orderBy != null) {
                el.orderBy(orderBy + " " + orderDir);
            }

            return el;
        }
    }

    /**
     * 게시물 목록 조회
     *
     * when: 특정 프로젝트의 게시물 목록을 검색 / 조회 할 때 사용
     *
     * 접근 권한을 체크하고 접근 권한이 없다면 forbidden 처리한다.
     * 검색 조건에 matching 되는 게시물 목록과 공지사항을 가져와서 표시한다.
     *
     * @param userName 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @param pageNum 페이지 번호
     * @return
     */
    @IsAllowed(value = Operation.READ, resourceType = ResourceType.PROJECT)
    public static Result posts(String userName, String projectName, int pageNum) {
        Project project = ProjectApp.getProject(userName, projectName);

        Form<SearchCondition> postParamForm = new Form<>(SearchCondition.class);
        SearchCondition searchCondition = postParamForm.bindFromRequest().get();
        searchCondition.pageNum = pageNum - 1;
        if(searchCondition.orderBy.equals("id")) {
            searchCondition.orderBy = "createdDate";
        }

        ExpressionList<Posting> el = searchCondition.asExpressionList(project);
        el.eq("notice", false);
        Page<Posting> posts = el.findPagingList(ITEMS_PER_PAGE).getPage(searchCondition.pageNum);
        List<Posting> notices = Posting.findNotices(project);

        return ok(list.render("menu.board", project, posts, searchCondition, notices));
    }

    /**
     * 게시물 등록 폼
     *
     * when: 새로운 게시물을 작성할 때 사용
     *
     * 공지작성 권한이 있다면 등록 폼에서 공지사항 여부 체크 박스를 활성화한다.
     *
     * @param userName 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @return
     */
    @With(AnonymousCheckAction.class)
    @IsCreatable(ResourceType.BOARD_POST)
    public static Result newPostForm(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);

        boolean isAllowedToNotice =
                AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, ResourceType.BOARD_NOTICE);

        return ok(create.render("post.new", new Form<>(Posting.class), project, isAllowedToNotice));
    }

    /**
     * 게시물 등록
     *
     * when: 게시물 작성 후 저장시 호출
     *
     * 게시물 등록 권한을 확인하여, 권한이 없다면 forbidden 처리한다.
     *
     * @param userName 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @return
     */
    @Transactional
    @IsCreatable(ResourceType.BOARD_POST)
    public static Result newPost(String userName, String projectName) {
        Form<Posting> postForm = new Form<>(Posting.class).bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);

        if (postForm.hasErrors()) {
            boolean isAllowedToNotice =
                    AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, ResourceType.BOARD_NOTICE);
            return badRequest(create.render("error.validation", postForm, project, isAllowedToNotice));
        }

        final Posting post = postForm.get();

        if (post.body == null) {
            return status(REQUEST_ENTITY_TOO_LARGE, ErrorViews.RequestTextEntityTooLarge.render());
        }

        post.createdDate = JodaDateUtil.now();
        post.updatedDate = JodaDateUtil.now();
        post.setAuthor(UserApp.currentUser());
        post.project = project;

        post.save();

        attachUploadFilesToPost(post.asResource());

        NotificationEvent.afterNewPost(post);

        return redirect(routes.BoardApp.post(project.owner, project.name, post.getNumber()));
    }

    /**
     * 게시물 조회
     *
     * when: 게시물 상세 조회시 호출
     *
     * 접근 권한을 체크하고 접근 권한이 없다면 forbidden 처리한다.
     * 게시물ID에 해당하는 내용이 없다면, 해당하는 게시물이 없음을 알린다.
     *
     * ACCEPT 헤더에 json이 있을 경우, POST 내용을 JSON으로 보낸다.
     *
     * @param userName 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @param number 게시물number
     * @return
     */
    @IsAllowed(value = Operation.READ, resourceType = ResourceType.BOARD_POST)
    public static Result post(String userName, String projectName, Long number) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        Posting post = Posting.findByNumber(project, number);

        if(request().getHeader("Accept").contains("application/json")) {
            ObjectNode json = Json.newObject();
            json.put("title", post.title);
            json.put("body", post.body);
            json.put("author", post.authorLoginId);
            return ok(json);
        }

        Form<PostingComment> commentForm = new Form<>(PostingComment.class);
        return ok(view.render(post, commentForm, project));
    }

    /**
     * 게시물 수정 폼
     *
     * when: 게시물 수정할때 호출
     *
     * 수정 권한을 체크하고 접근 권한이 없다면 forbidden 처리한다.
     * 공지작성 권한이 있다면 등록 폼에서 공지사항 여부 체크 박스를 활성화한다.
     *
     * @param owner 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @param number 게시물number
     * @return
     */
    @With(AnonymousCheckAction.class)
    @IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.BOARD_POST)
    public static Result editPostForm(String owner, String projectName, Long number) {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Posting posting = Posting.findByNumber(project, number);

        Form<Posting> editForm = new Form<>(Posting.class).fill(posting);
        boolean isAllowedToNotice = ProjectUser.isAllowedToNotice(UserApp.currentUser(), project);

        return ok(edit.render("post.modify", editForm, posting, number, project, isAllowedToNotice));
    }

    /**
     * 게시물 수정
     *
     * when: 게시물 수정 후 저장시 호출
     *
     * 수정된 내용을 반영하고 게시물 목록 첫 페이지로 돌아간다
     *
     * @param userName 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @param number 게시물number
     * @return
     * @see AbstractPostingApp#editPosting(models.AbstractPosting, models.AbstractPosting, play.data.Form
     */
    @Transactional
    @With(NullProjectCheckAction.class)
    public static Result editPost(String userName, String projectName, Long number) {
        Form<Posting> postForm = new Form<>(Posting.class).bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);

        if (postForm.hasErrors()) {
            boolean isAllowedToNotice =
                    AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, ResourceType.BOARD_NOTICE);
            return badRequest(edit.render("error.validation", postForm, Posting.findByNumber(project, number), number, project, isAllowedToNotice));
        }

        final Posting post = postForm.get();
        final Posting original = Posting.findByNumber(project, number);
        Call redirectTo = routes.BoardApp.post(project.owner, project.name, number);
        Runnable updatePostingBeforeUpdate = new Runnable() {
            @Override
            public void run() {
                post.comments = original.comments;
            }
        };

        return editPosting(original, post, postForm, redirectTo, updatePostingBeforeUpdate);
    }

    /**
     * 게시물 삭제
     *
     * when: 게시물 삭제시 호출
     *
     * 게시물을 삭제하고 게시물 목록 첫 페이지로 돌아간다
     *
     * @param owner 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @param number 게시물number
     * @return
     * @see controllers.AbstractPostingApp#delete(play.db.ebean.Model, models.resource.Resource, play.mvc.Call)
     */
    @Transactional
    @IsAllowed(value = Operation.DELETE, resourceType = ResourceType.BOARD_POST)
    public static Result deletePost(String owner, String projectName, Long number) {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Posting posting = Posting.findByNumber(project, number);
        Call redirectTo = routes.BoardApp.posts(project.owner, project.name, 1);

        return delete(posting, posting.asResource(), redirectTo);
    }

    /**
     * 댓글 작성
     *
     * when: 게시물에 댓글 작성 후 저장시 호출
     *
     * validation check 하여 오류가 있다면 bad request
     * 작성된 댓글을 저장하고 게시물 상세화면으로 돌아간다
     *
     * @param owner 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @param number 게시물number
     * @return
     * @throws IOException
     * @see controllers.AbstractPostingApp#newComment(models.Comment, play.data.Form, play.mvc.Call, Runnable)
     */
    @Transactional
    @IsAllowed(value = Operation.READ, resourceType = ResourceType.BOARD_POST)
    @IsCreatable(ResourceType.NONISSUE_COMMENT)
    public static Result newComment(String owner, String projectName, Long number) throws IOException {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        final Posting posting = Posting.findByNumber(project, number);
        Call redirectTo = routes.BoardApp.post(project.owner, project.name, number);
        Form<PostingComment> commentForm = new Form<>(PostingComment.class)
                .bindFromRequest();

        if (commentForm.hasErrors()) {
            return badRequest(views.html.error.badrequest.render("error.validation", project));
        }

        final PostingComment comment = commentForm.get();

        return newComment(comment, commentForm, redirectTo, new Runnable() {
            @Override
            public void run() {
                comment.posting = posting;
            }
        });
    }

    /**
     * 댓글 삭제
     *
     * when: 댓글 삭제시 호출
     *
     * 댓글을 삭제하고 게시물 상세화면으로 돌아간다
     *
     * @param userName 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @param number 게시물number
     * @param commentId 댓글ID
     * @return
     * @see controllers.AbstractPostingApp#delete(play.db.ebean.Model, models.resource.Resource, play.mvc.Call)
     */
    @Transactional
    @With(NullProjectCheckAction.class)
    public static Result deleteComment(String userName, String projectName, Long number, Long commentId) {
        Comment comment = PostingComment.find.byId(commentId);
        Project project = comment.asResource().getProject();
        Call redirectTo = routes.BoardApp.post(project.owner, project.name, number);

        return delete(comment, comment.asResource(), redirectTo);
    }
}
