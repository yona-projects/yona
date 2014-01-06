package models;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;

import models.enumeration.RoleType;
import play.db.ebean.Model;

@Entity
public class Role extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, Role> find = new Finder<>(Long.class,
            Role.class);

    @Id
    public Long id;

    public String name;
    public boolean active;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL)
    public List<ProjectUser> projectUsers;

    public static Role findById(Long id) {
        return find.byId(id);
    }

    public static Role findByRoleType(RoleType roleType) {
        return find.byId(roleType.roleType());
    }

    public static Role findByName(String name) {
        return find.where().eq("name", name).findUnique();
    }

    /**
     * 프로젝트와 관련된 롤들의 목록을 반환합니다.
     *
     * @return
     */
    public static List<Role> getActiveRoles() {
        return find.where().eq("active", true).findList();
    }

    /**
     * 해당 유저가 해당 프로젝트에서 가지고 있는 롤을 제공합니다.
     *
     * @param userId
     * @param projectId
     * @return
     */
    public static Role findRoleByIds(Long userId, Long projectId) {
        return find.where()
                .eq("projectUsers.user.id", userId)
                .eq("projectUsers.project.id", projectId).findUnique();
    }
}
