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
	@Constraints.Pattern("^[a-zA-Z0-9_]*$")
	public String name;
	public String overview; // 프로젝트 설명
	public boolean share_option; // 프로젝트 공개설정
	public String vcs; // Version Control System (vcs) 코드 관리 시스템
	public String url; // 프로젝트 url
	public Long owner; // 프로젝터 소유자
	
	public static Finder<Long, Project> find = new Finder(Long.class, Project.class);

	public static Long create(Project newProject){
		newProject.save();
		newProject.url = "http://localhost:9000/project/" + Long.toString(newProject.id); // default url 설정
		newProject.update();
	    return newProject.id;
	}
	
	public static Long update(Project updatedProject, Long id){
		updatedProject.update(id);
		return id;
	}
	
	public static void delete(Project deletedProject){
		deletedProject.delete();
	}
	
	public static Project findById(Long id) {
        return find.byId(id);
    }
	
	public static List<Project> findByOwner(Long owner) { // user 부분 완료 전 임시 method
		return find.where().eq("owner", owner).findList();
	}

}
