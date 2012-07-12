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
import views.html.board.*;

public class BoardApp extends Controller {

    public static Result boardList(int pageNum) {
        return ok(postList.render("게시판", Post.findOnePage(pageNum)));
    }

    public static Result newPost() {
        return ok(newPost.render("새 게시물", new Form<Post>(Post.class)));
    }

    public static Result savePost() {
        // TODO form에 있는 정보 받아와서 DB에저장 파일 세이브도 구현할것
        Form<Post> postForm = new Form<Post>(Post.class).bindFromRequest();

        if (postForm.hasErrors()) {
            return ok("입력값이 잘못되었습니다.");
        } else {
            Post post = postForm.get();
            post.userId = User.findByName("hobi").id;

            MultipartFormData body = request().body().asMultipartFormData();

            FilePart filePart = body.getFile("filePath");

            if (filePart != null) {
                File saveFile = new File("public/uploadFiles/" + filePart.getFilename());
                filePart.getFile().renameTo(saveFile);
                post.filePath = filePart.getFilename();
            }
            Post.write(post);
        }
        return redirect(routes.BoardApp.boardList(1));
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
            return TODO;

        } else {
            Comment comment = commentForm.get();
            comment.postId = postId;
            comment.userId = User.findByName("hobi").id;

            MultipartFormData body = request().body().asMultipartFormData();

            FilePart filePart = body.getFile("filePath");

            if (filePart != null) {
                File saveFile = new File("public/uploadFiles/" + filePart.getFilename());
                filePart.getFile().renameTo(saveFile);

                comment.filePath = filePart.getFilename();
            }

            Comment.write(comment);

            return redirect(routes.BoardApp.post(postId));
        }
    }

    public static Result delete(Long postId) {
        Post.delete(postId);
        return redirect(routes.BoardApp.boardList(1));
    }
    
    public static Result editPost(Long postId) {
        return TODO;
    }
}
