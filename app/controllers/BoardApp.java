package controllers;

import java.util.*;

import models.Article;
import models.Reply;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;

import views.html.board.*;

public class BoardApp extends Controller {
	
	final static Form<Article> articleform = new Form<Article>(Article.class);
	
	final static Form<Reply> replyForm = new Form<Reply>(Reply.class);
	
	public static Result boardList(int pageNum)
	{
		return ok(list.render("게시판", Article.findOnePage(pageNum)));
	}

	public static Result newArticle() {
		return ok(newArticle.render("New Board", new Form<Article>(Article.class)));
	}
	
	public static Result newArticleSave()
	{
		//TODO form에 있는 정보 받아와서 DB에저장 파일 세이브도 구현할것
		Form<Article> form = articleform.bindFromRequest();
		if(form.hasErrors())
			return ok("입력값이 잘못되었습니다.");
		else
			form.get().save();
		return redirect(routes.BoardApp.boardList(1));
	}
	
	public static Result showDetail(int articleNum)
	{
		Article article = Article.findById(articleNum);
		List<Reply> replys = Reply.findArticlesReply(articleNum);
		if(article == null)
			return ok(notExsitPage.render("존재하지 않는 게시물"));
		else
			return ok(detail.render(article, replys, replyForm));
		
	}
	public static Result saveReply(int articleNum)
	{
		Form<Reply> form = replyForm.bindFromRequest();
		Map<String, String> data = form.data();
		data.put("articleNum", "" + articleNum);
		form = form.bind(data);
		Reply.write(form.get());
		return redirect(routes.BoardApp.showDetail(articleNum));
	}
	public static Result delete(int articleNum)
	{
		return TODO;
	}
}
