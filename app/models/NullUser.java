package models;

import controllers.UserApp;
import models.enumeration.ResourceType;
import models.resource.Resource;
import play.i18n.Messages;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NullUser extends User {
    private static final long serialVersionUID = -1L;

    public NullUser(){
        this.id = -1l;
        this.name = Messages.get("user.notExists.name");
        this.loginId = "";
        this.email = "";
        this.createdDate = new Date();
        this.avatarUrl = UserApp.DEFAULT_AVATAR_URL;
    }

    public List<Project> myProjects(){
        return new ArrayList<>();
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
