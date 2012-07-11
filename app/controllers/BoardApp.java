/**
 * @author Ahn Hyeok Jun
 */

package controllers;

import java.io.File;
import java.util.*;

import models.Article;
import models.Reply;
import models.User;
import play.data.*;
import play.mvc.*;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;

import views.html.board.*;

public class BoardApp extends Controller {

    public static Result boardList(int pageNum) {
        return ok(list.render("게시판", Article.findOnePage(pageNum)));
    }

    public static Result newArticle() {
        return ok(newArticle.render("새 게시물", new Form<Article>(Article.class)));
    }

    public static Result saveArticle() {
        // TODO form에 있는 정보 받아와서 DB에저장 파일 세이브도 구현할것
        Form<Article> form = new Form<Article>(Article.class).bindFromRequest();

        if (form.hasErrors()) {
            return ok("입력값이 잘못되었습니다.");
        } else {
            Article article = form.get();
            article.writerId = User.findByName("hobi").id;

            MultipartFormData body = request().body().asMultipartFormData();

            FilePart filePart = body.getFile("filePath");

            if (filePart != null) {
                File saveFile = new File("public/uploadFiles/" + filePart.getFilename());
                filePart.getFile().renameTo(saveFile);
                article.filePath = saveFile.getAbsolutePath();
            }
            Article.write(article);
        }
        return redirect(routes.BoardApp.boardList(1));
    }

    public static Result article(Long articleNum) {
        Article article = Article.findById(articleNum);
        List<Reply> replys = Reply.findArticlesReply(articleNum);
        if (article == null) {
            return ok(notExsitPage.render("존재하지 않는 게시물"));
        } else {
            Form<Reply> replyForm = new Form<Reply>(Reply.class);
            return ok(detail.render(article, replys, replyForm));
        }
    }

    public static Result saveReply(Long articleNum) {
        Form<Reply> replyForm = new Form<Reply>(Reply.class).bindFromRequest();

        if (replyForm.hasErrors()) {
            return TODO;

        } else {
            Reply reply = replyForm.get();
            reply.articleNum = articleNum;
            reply.writerId = User.findByName("hobi").id;

            MultipartFormData body = request().body().asMultipartFormData();

            FilePart filePart = body.getFile("filePath");

            if (filePart != null) {
                File saveFile = new File("public/uploadFiles/" + filePart.getFilename());
                filePart.getFile().renameTo(saveFile);

                reply.filePath = saveFile.getAbsolutePath();
            }

            Reply.write(reply);

            return redirect(routes.BoardApp.article(articleNum));
        }
    }

    public static Result delete(Long articleNum) {
        Article.delete(articleNum);
        return redirect(routes.BoardApp.boardList(1));
    }
}
