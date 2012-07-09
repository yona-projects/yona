package models;

/*
 * @author: Hwi Ahn
 */

import javax.persistence.*;

import play.data.validation.*;
import play.db.ebean.*;

import java.util.*;

@Entity
public class Project extends Model{
	
	@Id
	public Long id;
	
	@Constraints.Required
	public String name;
	
	public String overview; // 프로젝트 설명
	
	public boolean share_option; // 프로젝트 공개설정
	
	public String vcs; // Version Control System (vcs) 코드 관리 시스템

/*
	public Long owner; // 프로젝트 생성자 id - 나중에 연결
	public List<Long> participants; // 프로젝트 참여자 id 리스트 - 나중에 연결
*/

	public static Finder<Long, Project> find = new Finder(Long.class, Project.class);

	public static Long create(Project newProject){
		newProject.save();
		return newProject.id;
	}
	
	public static Project findById(Long id) {
        return find.ref(id);
    }
	
	public Long getId() {
        return id;
    }

/*	public static List<String>	vcs_list(){
		List<String> all = new ArrayList<String>();
		all.add("GIT");
		all.add("Subversion");
	
		return all;
	}*/
	
/*	public static List<Project> all() {
		return find.all();
	}*/

}
