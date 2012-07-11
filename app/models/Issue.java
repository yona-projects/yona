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
	public Long userId;					//글쓴이
	
	@Constraints.Required
    public String title;				//제목
	@Constraints.Required
	public String body;					//글 내용
	@Constraints.Required
    public int status;					//이슈 상태
    @Formats.DateTime(pattern="YYYY/MM/DD/hh/mm/ss")
	public Date date;					//이슈 작성일
	//세부정보
    public int issueType;				//이슈유형
    public User reposibleMember;		//담당자
    public String comp;					//컴포넌트
    public Milestone milestone;			//적용된 마일스톤
    
	//public int replyNum;
	
	//public String filePath;
    
    public static final int STATUS_ENROLLED 	= 1; 	//등록
    public static final int STATUS_ASSINGED 	= 2;	//진행중
    public static final int STATUS_SOLVED 		= 3;	//해결
    public static final int STATUS_CLOSED 		= 4;	//닫힘  
	
    //TODO_추후 세부정보의 해당 이슈 유형이 결정나면 추후 설정
    public static final int issueType_1 	= 1; 	//등록
    public static final int issueType_2 	= 2;	//진행중
    public static final int issueType_3 		= 3;	//해결
    public static final int issueType_4 		= 4;	//닫힘
    
    
    private static Finder<Long, Issue> find = new Finder<Long, Issue>(Long.class, Issue.class);

	public static Long create(Issue issue) {
		issue.save();
		return issue.id;
		}
	
	public static void delete(Long id) {
		find.ref(id).delete();
	}
	
	public static Issue findById(Long id) {
		return find.byId(id);
    }
    public static List<Issue> findByTitle(String word) {
        return find.where().contains("title", word).findList();
    }
    
    //TODO_추후에 IssueReply 모델에 추가하면서 기능 추가할것
    public static void replyAdd(int issueId){
    	
    }
  
}
