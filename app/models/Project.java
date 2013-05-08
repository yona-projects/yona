package models;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.persistence.*;
import javax.servlet.ServletException;
import javax.validation.constraints.NotNull;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.ExpressionList;
import models.enumeration.ResourceType;
import models.enumeration.RoleType;
import models.resource.Resource;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.joda.time.Duration;

import org.tmatesoft.svn.core.SVNException;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.db.ebean.Transactional;
import playRepository.Commit;
import playRepository.GitRepository;
import playRepository.RepositoryService;
import utils.JodaDateUtil;

import com.avaje.ebean.Page;

@Entity
public class Project extends Model {
	private static final long serialVersionUID = 1L;
	public static Finder<Long, Project> find = new Finder<Long, Project>(
			Long.class, Project.class);

    public static final int PROJECT_COUNT_PER_PAGE = 10;

    public static Comparator<Project> sortByNameWithIgnoreCase = new SortByNameWithIgnoreCase();
    public static Comparator<Project> sortByNameWithIgnoreCaseDesc = new SortByNameWithIgnoreCaseDesc();
    public static Comparator<Project> sortByDate = new SortByDate();
    public static Comparator<Project> sortByDateDesc = new SortByDateDesc();

	@Id
	public Long id;

	@Constraints.Required
	@Constraints.Pattern("^[-a-zA-Z0-9_]*$")
	@Constraints.MinLength(2)
	public String name;

	public String overview;
	public String vcs;
	public String siteurl;
    public String owner;

	public boolean isPublic;

    public Date createdDate;

	@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
	public Set<Issue> issues;

	@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
	public List<ProjectUser> projectUser;

	@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
	public List<Posting> posts;

	@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
	public List<Milestone> milestones;

    private long lastIssueNumber;
    private long lastPostingNumber;

    @ManyToMany
    public Set<Tag> tags;

    public String getCreatedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd");
        return sdf.format(this.createdDate);
    }

    public static Long create(Project newProject) {
		newProject.siteurl = "http://localhost:9000/" + newProject.name;
        newProject.createdDate = new Date();
        newProject.save();
		ProjectUser.assignRole(User.SITE_MANAGER_ID, newProject.id,
				RoleType.SITEMANAGER);
		return newProject.id;
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
	 * @param userName 사용자이름
	 * @param projectName 프로젝트이름
	 * @return
	 */
	public static boolean exists(String userName, String projectName) {
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
	 * @param userId
	 * @return
	 */
	public static List<Project> findProjectsByMember(Long userId) {
        return find.where().eq("projectUser.user.id", userId).findList();
	}

    /**
     * 해당 유저가 속해있는 프로젝트들의 리스트를 필터를 적용해서 보여준다.
     *
     * @param userId 유저 아이디
     * @param  orderString 오름차순/내림차순 등 필터로 사용할 스트링
     * @return 정렬된 프로젝트 목록
     */
    public static List<Project> findProjectsByMemberWithFilter(Long userId, String orderString) {
        List<Project> userProjectList = find.where().eq("projectUser.user.id", userId).findList();
        if( orderString == null ){
            return userProjectList;
        }

        List<Project> filteredList = Ebean.filter(Project.class).sort(orderString).filter(userProjectList);
        Collections.sort(filteredList, determineComparator(orderString));
        return filteredList;
    }

    /**
     * 요청 문자열로 부터 정렬 방법을 정한다.
     * @param orderString
     * @return ordered
     */
    private static Comparator<? super Project> determineComparator(String orderString) {  //TODO: Some ugly coding...
        if( orderString.contains("name desc")){
            return sortByNameWithIgnoreCaseDesc;
        } else if ( orderString.contains("name") ) {
            return sortByNameWithIgnoreCase;
        } else if ( orderString.contains("createdDate desc") ){
            return sortByDateDesc;
        } else if ( orderString.contains("createdDate") ){
            return sortByDate;
        } else {  // TODO: another sorting case doesn't exist in this moment
            throw new UnsupportedOperationException("unsupported sorting type");
        }
    }

    public static Page<Project> projects(int pageNum) {
		return find.findPagingList(25).getPage(pageNum);
	}

	public static int countByState(String state) {
		if (state == "all") {
			return find.findRowCount();
		} else if (state == "public") {
			return find.where().eq("isPublic", true).findRowCount();
		} else if (state == "private") {
			return find.where().eq("isPublic", false).findRowCount();
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
		return this.createdDate;
	}

	public Duration ago() {
		return JodaDateUtil.ago(lastUpdateDate());
	}

	public String readme() {
        try {
            String realFileName = getReadmeFileName();
            return new String(RepositoryService.getRepository(this).getRawFile(realFileName));
        } catch (Exception e) {
            return null;
        }
	}

    private String getReadmeFileName() throws IOException, GitAPIException, SVNException, ServletException {
        String baseFileName = "README.md";
        ObjectNode objectNode = RepositoryService.getRepository(this).findFileInfo("/");
        List<JsonNode> nodes = objectNode.findValues("data");
        for(JsonNode node : nodes) {
            Iterator<String> fieldNames = node.getFieldNames();
            while(fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if(fieldName.toLowerCase().equals(baseFileName.toLowerCase())) {
                    return fieldName;
                }
            }
        }
        return baseFileName;
    }

    @Transactional
    public Long increaseLastIssueNumber() {
        lastIssueNumber++;
        update();
        return lastIssueNumber;
    }

    @Transactional
    public Long increaseLastPostingNumber() {
        lastPostingNumber++;
        update();
        return lastPostingNumber;
    }

    public static class SortByNameWithIgnoreCase implements Comparator<Project> {
        public int compare(Project o1, Project o2) {
            return o1.name.toLowerCase().compareTo(o2.name.toLowerCase());
        }
    }

    public static class SortByNameWithIgnoreCaseDesc implements Comparator<Project> {
        public int compare(Project o1, Project o2) {
            return -sortByNameWithIgnoreCase.compare(o1, o2);
        }
    }

    public static class SortByDate implements Comparator<Project> {
        public int compare(Project o1, Project o2) {
            return o1.createdDate.compareTo(o2.createdDate);
        }
    }

    public static class SortByDateDesc implements Comparator<Project> {
        public int compare(Project o1, Project o2) {
            return -sortByDate.compare(o1, o2);
        }
    }

	public Resource tagsAsResource() {
	    return new Resource() {

            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Project getProject() {
                return Project.this;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.PROJECT_TAGS;
            }

	    };
	}

	public Resource asResource() {
	    return new Resource() {

            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Project getProject() {
                return null;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.PROJECT;
            }

	    };
	}

    public User getOwnerByName(String userId){
        return User.findByLoginId(userId);
    }

    public Boolean tag(Tag tag) {
       if (tags.contains(tag)) {
            // Return false if the tag has been already attached.
            return false;
        }

        // Attach new tag.
        tags.add(tag);
        update();

        return true;
    }

    public void untag(Tag tag) {
        tag.projects.remove(this);
        if (tag.projects.size() == 0) {
            tag.delete();
        } else {
            tag.update();
        }
    }

    public boolean isOwner(User user) {
        return owner.toLowerCase().equals(user.loginId.toLowerCase());
    }

    public String toString() {
        return owner + "/" + name;
    }
}
