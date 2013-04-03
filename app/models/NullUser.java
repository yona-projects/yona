package models;

import com.avaje.ebean.Page;
import controllers.UserApp;
import models.enumeration.Direction;
import models.enumeration.Matching;
import models.enumeration.ResourceType;
import models.enumeration.RoleType;
import models.resource.Resource;
import models.support.FinderTemplate;
import models.support.OrderParams;
import models.support.SearchParams;
import play.data.format.Formats;
import play.data.validation.Constraints.Email;
import play.data.validation.Constraints.Pattern;
import play.data.validation.Constraints.Required;
import play.db.ebean.Model;
import utils.JodaDateUtil;

import javax.persistence.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NullUser extends User {
    private static final long serialVersionUID = -1L;
    public Long id = -1l;
    public String name = "Guest";

    public String loginId = "guest";
    public String password;
    public String passwordSalt;
    public String email = "nforge@nhn.com";

    public String avatarUrl = UserApp.DEFAULT_AVATAR_URL;

    public List<Project> myProjects(){
        return null;
    }

    public boolean isAnonymous() {
        return true;
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
                return ResourceType.USER;
            }
        };
    }

    public boolean isSiteManager() {
        return false;
    }
}
