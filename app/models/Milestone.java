package models;

import java.util.*;

import javax.persistence.*;

import play.db.ebean.*;
import play.data.validation.*;
import play.data.format.*;



/**
 * Milestone entity managed by Ebean
 */
@Entity
public class Milestone extends Model{
	
	@Id
	public Long id;
	
	@Constraints.Required
	public String versionName;
	
	@Constraints.Required
	@Formats.DateTime(pattern="yyyy-MM-dd")
	public Date dueDate;
	
	@Constraints.Required
	public String contents;
	
	public int numClosedIssues;
	
	public int numOpenIssues;
	
	// 추후 추가해야할 듯
	//public int projectId; //foreign key
	
	
	
	public static Finder<Long, Milestone> find = new Finder<Long, Milestone>(Long.class, Milestone.class);
	
	
	
	public static List<Milestone> findOnePage(int pageNum){
		
		return find.findPagingList(10).getPage(pageNum-1).getList(); 
		//return	new ArrayList<Milestone>();
	}
	
	public static void create(Milestone milestone){
		milestone.save();
		
	}
	
	public static void delete(Long id){
		find.ref(id).delete();
	}
	
	
}
