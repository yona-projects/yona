package models;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.servlet.ServletException;

import models.enumeration.RoleType;
import models.task.TaskBoard;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.joda.time.Duration;

import play.Logger;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import playRepository.Commit;
import playRepository.GitRepository;
import playRepository.RepositoryService;
import utils.JodaDateUtil;

import com.avaje.ebean.Page;

/**
 * 
 * @author "Hwi Ahn"
 */

@Entity
public class Project extends Model {
	private static final long serialVersionUID = 1L;
	public static Finder<Long, Project> find = new Finder<Long, Project>(
			Long.class, Project.class);

	@Id
	public Long id;

	@Constraints.Required
	@Constraints.Pattern("^[a-zA-Z0-9_][-a-zA-Z0-9_]+[^-]$")
	public String name;

	public String overview;
	public String vcs;
	public String siteurl;
	public String logoPath;
	public String owner;

	public boolean share_option;
	public boolean isAuthorEditable;

	public Date date;

	@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
	public List<Issue> issues;

	@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
	public List<ProjectUser> projectUser;

	@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
	public List<Post> posts;

	@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
	public List<Milestone> milestones;

	@OneToOne(mappedBy = "project", cascade = CascadeType.ALL)
	public TaskBoard taskBoard;

	public static Long create(Project newProject) {
		newProject.siteurl = "http://localhost:9000/" + newProject.name;
		newProject.save();
		ProjectUser.assignRole(User.SITE_MANAGER_ID, newProject.id,
				RoleType.SITEMANAGER);
		return newProject.id;
	}

	public static void delete(Long id) {
		Project.find.byId(id).delete();
	}

	public static Page<Project> findByName(String name, int pageSize,
			int pageNum) {
		return find.where().ilike("name", "%" + name + "%")
				.findPagingList(pageSize).getPage(pageNum);
	}

	public static Project findByNameAndOwner(String userName, String projectName) {
		return find.where().eq("name", projectName).eq("owner", userName)
				.findUnique();
	}

	/**
	 * 해당 프로젝트가 존재하는지 여부를 검사합니다. 해당 파라미터에 대하여 프로젝트가 존재하면 true, 존재하지 않으면 false를
	 * 반환합니다.
	 * 
	 * @param userName
	 * @param projectName
	 * @return
	 */
	public static boolean isProject(String userName, String projectName) {
		int findRowCount = find.where().eq("name", projectName)
				.eq("owner", userName).findRowCount();
		return (findRowCount != 0) ? true : false;
	}

	/**
	 * 프로젝트 이름을 해당 이름(projectName)으로 변경이 가능한지 검사합니다.
	 * 
	 * @param id
	 * @param userName
	 * @param projectName
	 * @return
	 */
	public static boolean projectNameChangeable(Long id, String userName,
			String projectName) {
		int findRowCount = find.where().eq("name", projectName)
				.eq("owner", userName).ne("id", id).findRowCount();
		return (findRowCount == 0) ? true : false;
	}

	/**
	 * 해당 유저가 속해있는 프로젝트들 중에서 해당 유저가 유일한 Manager인 프로젝트가 있는지 검사하고, 있다면 그 프로젝트들의
	 * 리스트를 반환합니다.
	 * 
	 * @param userId
	 * @return
	 */
	public static List<Project> isOnlyManager(Long userId) {
		List<Project> projects = find.select("id").select("name").where()
				.eq("projectUser.user.id", userId)
				.eq("projectUser.role.id", RoleType.MANAGER.roleType())
				.findList();
		Iterator<Project> iterator = projects.iterator();
		while (iterator.hasNext()) {
			Project project = iterator.next();
			if (ProjectUser.checkOneMangerPerOneProject(project.id)) {
				iterator.remove();
			}
		}

		return projects;
	}

	/**
	 * 해당 유저가 속해있는 프로젝트들의 리스트를 제공합니다.
	 * 
	 * @param ownerId
	 * @return
	 */
	public static List<Project> findProjectsByMember(Long userId) {
		return find.where().eq("projectUser.user.id", userId).findList();
	}

	public static Page<Project> projects(int pageNum) {
		return find.findPagingList(25).getPage(pageNum);
	}

	public static int countByState(String state) {
		if (state == "all") {
			return find.findRowCount();
		} else if (state == "public") {
			return find.where().eq("share_option", true).findRowCount();
		} else if (state == "private") {
			return find.where().eq("share_option", false).findRowCount();
		} else {
			return 0;
		}
	}

	public Date lastUpdateDate() {
		try {
			GitRepository gitRepo = new GitRepository(owner, name);
			List<String> branches = RepositoryService.getRepository(this)
					.getBranches();
			if (!branches.isEmpty()) {
				List<Commit> history = gitRepo.getHistory(0, 2, "HEAD");
				return history.get(0).getAuthorDate();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoHeadException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		} catch (UnsupportedOperationException e) {
			e.printStackTrace();
		} catch (ServletException e) {
			e.printStackTrace();
		}
		return this.date;
	}

	public Duration ago() {
		return JodaDateUtil.ago(lastUpdateDate());
	}

	public String readme() {
		try {
			return new String(RepositoryService.getRepository(this).getRawFile(
					"README.md"));
		} catch (Exception e) {
			return null;
		}
	}
}
