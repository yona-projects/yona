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
	
	public String url; // 프로젝트 url

/*
	public Long owner; // 프로젝트 생성자 id - 나중에 연결
	public List<Long> participants; // 프로젝트 참여자 id 리스트 - 나중에 연결
*/

	public static Finder<Long, Project> find = new Finder(Long.class, Project.class);

	public static Long create(Project newProject){
		newProject.save();
		newProject.url = "http://localhost:9000/project/" + Long.toString(newProject.id); // default url 설정
		newProject.update();
		return newProject.id;
	}
	
	public static Project findById(Long id) {
        return find.byId(id);
    }

/*	public static List<String>	vcs_list(){
		List<String> all = new ArrayList<String>();
		all.add("GIT");
		all.add("Subversion");
	
		return all;
	}*/
	
	public static List<Project> all() { // user 부분 완료 전 임시 method
		return find.all();
	}

}
