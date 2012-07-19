/**
 * @author Ahn Hyeok Jun
 */

package controllers;

import java.io.File;
import java.util.List;

import models.*;
import play.data.Form;
import play.mvc.*;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Http.Request;
import views.html.board.*;
import org.eclipse.jgit.http.server.*;

public class BoardApp extends Controller {

    public static Result boardList(int pageNum, String order, String key) {

        return ok(postList.render("게시판", Post.findOnePage(pageNum, order, key), order, key));
    }

    public static Result newPost() {
        return ok(newPost.render("새 게시물", new Form<Post>(Post.class)));
    }

    public static Result savePost() {
        Form<Post> postForm = new Form<Post>(Post.class).bindFromRequest();

        if (postForm.hasErrors()) {
            return ok(boardError.render("본문과 제목은 반드시 써야합니다.",
                    routes.BoardApp.newPost()));
        } else {
            Post post = postForm.get();
            post.userId = UserApp.userId();
            post.commentCount = 0;
            post.filePath = saveFile(request());
            Post.write(post);
        }
        return redirect(routes.BoardApp.boardList(1, Post.ORDER_DESCENDING, Post.ORDERING_KEY_ID));
    }

    public static Result post(Long postId) {
        Post post = Post.findById(postId);
        List<Comment> comments = Comment.findCommentsByPostId(postId);
        if (post == null) {
            return ok(notExsitPage.render("존재하지 않는 게시물"));
        } else {
            Form<Comment> commentForm = new Form<Comment>(Comment.class);
            return ok(views.html.board.post.render(post, comments, commentForm));
        }
    }

    public static Result saveComment(Long postId) {
        Form<Comment> commentForm = new Form<Comment>(Comment.class).bindFromRequest();

        if (commentForm.hasErrors()) {
            return ok(boardError.render("본문은 반드시 쓰셔야 합니다.", routes.BoardApp.post(postId)));

        } else {
            Comment comment = commentForm.get();
            comment.postId = postId;
            comment.userId = UserApp.userId();
            comment.filePath = saveFile(request());

            Comment.write(comment);

            return redirect(routes.BoardApp.post(postId));
        }
    }

    public static Result delete(Long postId) {
        Post.delete(postId);
        return redirect(routes.BoardApp.boardList(1, Post.ORDER_DESCENDING, Post.ORDERING_KEY_ID));
    }

    public static Result editPost(Long postId) {
        Post exsitPost = Post.findById(postId);
        Form<Post> editForm = new Form<Post>(Post.class).fill(exsitPost);

        if (UserApp.userId() == exsitPost.userId) {
            return ok(editPost.render("게시물 수정", editForm, postId));
        } else {
            return ok(boardError.render("글쓴이가 아닙니다.", routes.BoardApp.post(postId)));
        }
    }

    public static Result updatePost(Long postId) {
        Form<Post> postForm = new Form<Post>(Post.class).bindFromRequest();

        if (postForm.hasErrors()) {
            return ok("입력값이 잘못되었습니다.");
        } else {

            Post post = postForm.get();
            post.userId = UserApp.userId();
            post.id = postId;
            post.filePath = saveFile(request());

            Post.edit(post);
        }
        return redirect(routes.BoardApp.boardList(1, Post.ORDER_DESCENDING, Post.ORDERING_KEY_ID));
    }

    private static String saveFile(Request request) {
        MultipartFormData body = request.body().asMultipartFormData();

        FilePart filePart = body.getFile("filePath");

        if (filePart != null) {
            File saveFile = new File("public/uploadFiles/" + filePart.getFilename());
            filePart.getFile().renameTo(saveFile);
            return filePart.getFilename();
        }
        return null;
    }
}
