/**
 * @author Ahn Hyeok Jun
 */

package controllers;

import java.io.File;
import java.util.*;

import models.Post;
import models.Comment;
import models.User;
import play.data.*;
import play.mvc.*;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;

import views.html.board.*;

public class BoardApp extends Controller {

    public static Result boardList(int pageNum) {
        return ok(list.render("게시판", Post.findOnePage(pageNum)));
    }

    public static Result newArticle() {
        return ok(newArticle.render("새 게시물", new Form<Post>(Post.class)));
    }

    public static Result saveArticle() {
        // TODO form에 있는 정보 받아와서 DB에저장 파일 세이브도 구현할것
        Form<Post> form = new Form<Post>(Post.class).bindFromRequest();

        if (form.hasErrors()) {
            return ok("입력값이 잘못되었습니다.");
        } else {
            Post article = form.get();
            article.userId = User.findByName("hobi").id;

            MultipartFormData body = request().body().asMultipartFormData();

            FilePart filePart = body.getFile("filePath");

            if (filePart != null) {
                File saveFile = new File("public/uploadFiles/" + filePart.getFilename());
                filePart.getFile().renameTo(saveFile);
                article.filePath = saveFile.getAbsolutePath();
            }
            Post.write(article);
        }
        return redirect(routes.BoardApp.boardList(1));
    }

    public static Result article(Long articleNum) {
        Post article = Post.findById(articleNum);
        List<Comment> replys = Comment.findArticlesReply(articleNum);
        if (article == null) {
            return ok(notExsitPage.render("존재하지 않는 게시물"));
        } else {
            Form<Comment> replyForm = new Form<Comment>(Comment.class);
            return ok(detail.render(article, replys, replyForm));
        }
    }

    public static Result saveReply(Long articleNum) {
        Form<Comment> replyForm = new Form<Comment>(Comment.class).bindFromRequest();

        if (replyForm.hasErrors()) {
            return TODO;

        } else {
            Comment reply = replyForm.get();
            reply.postId = articleNum;
            reply.userId = User.findByName("hobi").id;

            MultipartFormData body = request().body().asMultipartFormData();

            FilePart filePart = body.getFile("filePath");

            if (filePart != null) {
                File saveFile = new File("public/uploadFiles/" + filePart.getFilename());
                filePart.getFile().renameTo(saveFile);

                reply.filePath = saveFile.getAbsolutePath();
            }

            Comment.write(reply);

            return redirect(routes.BoardApp.article(articleNum));
        }
    }

    public static Result delete(Long articleNum) {
        Post.delete(articleNum);
        return redirect(routes.BoardApp.boardList(1));
    }
}
