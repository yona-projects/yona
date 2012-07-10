package models;

import java.util.*;

import javax.persistence.*;

import com.avaje.ebean.Page;

import play.data.format.*;
import play.data.validation.*;
import play.db.ebean.*;


@Entity
public class Issue extends Model {
	private static final long serialVersionUID = 1L;
	
	@Id
    public Long id;
	public Long userId;
	
	@Constraints.Required
    public String title;
	
	@Constraints.Required
	public String body;
	
	@Constraints.Required
    public int status;
    
	@Formats.DateTime(pattern="YYYY/MM/DD/hh/mm/ss")
	public Date date;
	
	//public int replyNum;
	
	//public String filePath;
    
    public static final int STATUS_ENROLLED 	= 1; 	//등록
    public static final int STATUS_ASSINGED 	= 2;	//진행중
    public static final int STATUS_SOLVED 		= 3;	//해결
    public static final int STATUS_CLOSED 		= 4;	//닫힘  
	
    private static Finder<Long, Issue> find = new Finder<Long, Issue>(Long.class, Issue.class);

	public static Long create(Issue issue) {
		issue.save();
		return issue.id;
	}
	
	public static void delete(Long id){
		find.ref(id).delete();
	}
	
	public static void findById(Long id){
		find.ref(id);
	}
	
}
