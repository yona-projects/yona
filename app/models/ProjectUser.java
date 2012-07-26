package models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import models.enumeration.Direction;

import play.db.ebean.Model;

/**
 * @author "Hwi Ahn"
 * 
 */
@Entity
public class ProjectUser extends Model {
	private static final long serialVersionUID = 1L;

	@Id
	public Long id;
	@ManyToOne
	public User user;
	@ManyToOne
	public Project project;
	@ManyToOne
	public Role role;

	public ProjectUser(Long userId, Long projectId, Long roleId) {
		this.user = User.findById(userId);
		this.project = Project.findById(projectId);
		this.role = Role.findById(roleId);
	}

	private static Finder<Long, ProjectUser> find = new Finder<Long, ProjectUser>(
			Long.class, ProjectUser.class);

	public static ProjectUser findByIds(Long userId, Long projectId) {
		return find.where().eq("user.id", userId).eq("project.id", projectId)
				.findUnique();
	}

	public static List<User> findUsersByProject(Long projectId) {
		List<ProjectUser> projectUsers = find.where()
				.eq("project.id", projectId).findList();
		List<User> users = new ArrayList<User>();
		for (ProjectUser projectUser : projectUsers) {
			if (projectUser.role.id.equals(1l))
				users.add(0, User.findById(projectUser.user.id));
			else
				users.add(User.findById(projectUser.user.id));
		}
		return users;
	}

	public static List<Project> findProjectsByOwner(Long ownerId) {
		List<ProjectUser> projectUsers = find.where().eq("user.id", ownerId)
				.findList();
		List<Project> projects = new ArrayList<Project>();
		for (ProjectUser projectUser : projectUsers) {
			projects.add(Project.findById(projectUser.project.id));
		}
		return projects;
	}

	public static Role findRoleByIds(Long userId, Long projectId) {
		Long roleId = find.where().eq("user.id", userId)
				.eq("project.id", projectId).findUnique().role.id;
		return Role.findById(roleId);
	}

	public static void create(Long userId, Long projectId, Long roleId) {
		ProjectUser projectUser = new ProjectUser(userId, projectId, roleId);
		projectUser.save();
	}

	public static boolean update(Long userId, Long projectId, Long roleId) {
		ProjectUser projectUser = new ProjectUser(userId, projectId, roleId);
		ProjectUser oldProjectUser = ProjectUser.findByIds(userId, projectId);
		boolean returnValue = false;
		if (oldProjectUser.role.id.equals(1l)) {
			if (existManager(projectId)) {
				projectUser.update(oldProjectUser.id);
				returnValue = true;
			}
		} else {
			projectUser.update(oldProjectUser.id);
			returnValue = true;
		}
		return returnValue;
	}

	public static boolean delete(Long userId, Long projectId) {
		ProjectUser projectUser = ProjectUser.findByIds(userId, projectId);
		boolean returnValue = false;
		if (projectUser.role.id.equals(1l)) {
			if (existManager(projectId)) {
				projectUser.delete();
				returnValue = true;
			}
		} else {
			projectUser.delete();
			returnValue = true;
		}
		return returnValue;
	}

	public static boolean existManager(Long projectId) {
		int findRowCount = find.where()
				.eq("project.id", projectId).eq("role.id", 1l).findRowCount();
		return (findRowCount > 1) ? true : false;
	}

	/**
	 * 해당 프로젝트에 참가하고 있는 유저의 목록을 제공합니다.
	 * 
	 * @return
	 */
	public static Map<String, String> options(Long projectId) {
		LinkedHashMap<String, String> options = new LinkedHashMap<String, String>();
		for (User user : findUsersByProject(projectId)) {
			options.put(user.id.toString(), user.loginId);
		}
		return options;
	}

}
