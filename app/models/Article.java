package models;

import java.util.*;

import javax.persistence.*;

import play.data.*;
import play.data.format.*;
import play.data.validation.*;
import play.db.ebean.*;

@Entity
public class Article extends Model {
	
	public Article() {
		this.date = new Date();//XXX 이게 맞는지 모르겠음.
	}
	
	@Id
	public int articleNum;
	
	@Constraints.Required
	public String title;
	
	@Constraints.Required
	public String contents;
	
	@Constraints.Required
	public String writer;
	
	@Constraints.Required
	@Formats.DateTime(pattern="YYYY/MM/DD/hh/mm/ss")
	public Date date;
	
	public static Finder<Integer, Article> find = new Finder<Integer, Article>(Integer.class, Article.class);
	
	public static Article findById(int articleNum)
	{
		return find.byId(articleNum);
	}
	
	public static List<Article> findOnePage(int pageNum)
	{
		return find.findPagingList(25).getPage(pageNum - 1).getList();
	}
	
	public static void write(Article article)
	{
		article.save();
	}
	public static void delete(int articleNum)
	{
		find.byId(articleNum).delete();
	}
	
	public String calcPassTime()
	{
		//TODO 경계값 검사하면 망할함수. 나중에 라이브러리 쓸예정
		Calendar today = Calendar.getInstance();
		
		long dTimeMili = today.getTime().getTime() - this.date.getTime();
		
		
		Calendar dTime = Calendar.getInstance();
		dTime.setTimeInMillis(dTimeMili);
		
		if(dTimeMili < 60* 1000)
		{
			return "방금 전";
		}
		else if(dTimeMili < 60 * 1000 * 60)
		{
			return dTime.get(Calendar.MINUTE) + "분 전";
		}
		else if(dTimeMili < 60 * 1000 * 60 * 24)
		{
			return dTime.get(Calendar.HOUR) + "시간 전";
		}
		else if(dTimeMili < 60 * 1000 * 60 * 24 * 30)
		{
			return dTime.get(Calendar.DATE) + "일 전";
		}
		else if(dTimeMili < 60 * 1000 * 60 * 24 * 30 * 12)
		{
			return dTime.get(Calendar.MONDAY) + "달 전"; 
		}
		else 
		{
			return dTime.get(Calendar.YEAR) + "년 전";
		}
	}
}
