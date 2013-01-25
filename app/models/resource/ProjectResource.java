package models.resource;

import models.Project;
import models.enumeration.Resource;

public interface ProjectResource {
    public Long getId();
    public Project getProject();
    public Resource getType();
}