
package controllers;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;

import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;

import views.html.board.editPost;
import views.html.board.newPost;
import views.html.board.postList;
import views.html.board.notExistingPage;
import views.html.project.unauthorized;

import utils.AccessControl;
import utils.Callback;
import utils.JodaDateUtil;

import play.data.Form;
import play.mvc.Call;
import play.mvc.Result;

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
    public static Result posts(String userName, String projectName, int pageNum) {
        Project project = ProjectApp.getProject(userName, projectName);

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return forbidden(unauthorized.render(project));
        }

        Form<SearchCondition> postParamForm = new Form<SearchCondition>(SearchCondition.class);
        SearchCondition searchCondition = postParamForm.bindFromRequest().get();
        searchCondition.pageNum = pageNum - 1;
        if(searchCondition.orderBy.equals("id")) {
            searchCondition.orderBy = "createdDate";
        }

        ExpressionList<Posting> el = searchCondition.asExpressionList(project);
        Page<Posting> posts = el.findPagingList(ITEMS_PER_PAGE).getPage(searchCondition.pageNum);
        List<Posting> notices = Posting.findNotices(project);

        return ok(postList.render("menu.board", project, posts, searchCondition, notices));
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
    public static Result newPostForm(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);

        boolean isAllowedToNotice = ProjectUser.isAllowedToNotice(UserApp.currentUser(), project);

        return newPostingForm(project, ResourceType.BOARD_POST,
                newPost.render("board.post.new", new Form<Posting>(Posting.class), project, isAllowedToNotice));
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
    public static Result newPost(String userName, String projectName) {
        Form<Posting> postForm = new Form<Posting>(Posting.class).bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);

        if (!AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, ResourceType.BOARD_POST)) {
            return forbidden(unauthorized.render(project));
        }

        if (postForm.hasErrors()) {
            boolean isAllowedToNotice = ProjectUser.isAllowedToNotice(UserApp.currentUser(), project);
            return badRequest(newPost.render(postForm.errors().toString(), postForm, project, isAllowedToNotice));
        }

        Posting post = postForm.get();
        post.createdDate = JodaDateUtil.now();
        post.setAuthor(UserApp.currentUser());
        post.project = project;

        post.save();

        // Attach all of the files in the current user's temporary storage.
        Attachment.attachFiles(UserApp.currentUser().id, post.asResource());

        return redirect(routes.BoardApp.posts(project.owner, project.name, 1));
    }

    /**
     * 게시물 조회
     * 
     * when: 게시물 상세 조회시 호출
     * 
     * 접근 권한을 체크하고 접근 권한이 없다면 forbidden 처리한다.
     * 게시물ID에 해당하는 내용이 없다면, 해당하는 게시물이 없음을 알린다.
     * 
     * @param userName 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @param postId 게시물ID
     * @return
     */
    public static Result post(String userName, String projectName, Long postId) {
        Project project = ProjectApp.getProject(userName, projectName);
        Posting post = Posting.finder.byId(postId);
        
        if (post == null) {
            return notFound(notExistingPage.render("title.post.notExistingPage", project));
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), post.asResource(), Operation.READ)) {
            return forbidden(unauthorized.render(project));
        }

        Form<PostingComment> commentForm = new Form<PostingComment>(PostingComment.class);
        return ok(views.html.board.post.render(post, commentForm, project));
    }

    /**
     * 게시물 수정 폼
     * 
     * when: 게시물 수정할때 호출
     * 
     * 수정 권한을 체크하고 접근 권한이 없다면 forbidden 처리한다.
     * 공지작성 권한이 있다면 등록 폼에서 공지사항 여부 체크 박스를 활성화한다.
     *
     * @param userName 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @param postId 게시물ID
     * @return
     */
    public static Result editPostForm(String userName, String projectName, Long postId) {
        Posting posting = Posting.finder.byId(postId);
        Project project = ProjectApp.getProject(userName, projectName);

        if (!AccessControl.isAllowed(UserApp.currentUser(), posting.asResource(), Operation.UPDATE)) {
            return forbidden(unauthorized.render(project));
        }

        Form<Posting> editForm = new Form<Posting>(Posting.class).fill(posting);
        boolean isAllowedToNotice = ProjectUser.isAllowedToNotice(UserApp.currentUser(), project);

        return ok(editPost.render("board.post.modify", editForm, postId, project, isAllowedToNotice));
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
     * @param postId 게시물ID
     * @return
     * @see controllers.AbstractPostingApp#editPosting(models.AbstractPosting, models.AbstractPosting, play.data.Form, play.mvc.Call, utils.Callback)
     */
    public static Result editPost(String userName, String projectName, Long postId) {
        Form<Posting> postForm = new Form<Posting>(Posting.class).bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);
        final Posting post = postForm.get();
        final Posting original = Posting.finder.byId(postId);
        Call redirectTo = routes.BoardApp.posts(project.owner, project.name, 1);
        Callback updatePostingBeforeUpdate = new Callback() {
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
     * @param userName 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @param postingId 게시물ID
     * @return
     * @see controllers.AbstractPostingApp#delete(play.db.ebean.Model, models.resource.Resource, play.mvc.Call)
     */
    public static Result deletePost(String userName, String projectName, Long postingId) {
        Posting posting = Posting.finder.byId(postingId);
        Project project = posting.project;
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
     * @param userName 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @param postId 게시물ID
     * @return
     * @throws IOException
     * @see controllers.AbstractPostingApp#newComment(models.Comment, play.date.Form, play.mvc.Call, utils.Callback)
     */
    public static Result newComment(String userName, String projectName, Long postId) throws IOException {
        final Posting posting = Posting.finder.byId(postId);
        Project project = posting.project;
        Call redirectTo = routes.BoardApp.post(project.owner, project.name, postId);
        Form<PostingComment> commentForm = new Form<PostingComment>(PostingComment.class)
                .bindFromRequest();

        if (commentForm.hasErrors()) {
            return badRequest(commentForm.errors().toString());
        }

        final PostingComment comment = commentForm.get();

        return newComment(comment, commentForm, redirectTo, new Callback() {
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
     * @param postId 게시물ID
     * @param commentId 댓글ID
     * @return
     * @see controllers.AbstractPostingApp#delete(play.db.ebean.Model, models.resource.Resource, play.mvc.Call)
     */
    public static Result deleteComment(String userName, String projectName, Long postId,
            Long commentId) {
        Comment comment = PostingComment.find.byId(commentId);
        Project project = comment.asResource().getProject();
        Call redirectTo =
                routes.BoardApp.post(project.owner, project.name, comment.getParent().id);

        return delete(comment, comment.asResource(), redirectTo);
    }
}
