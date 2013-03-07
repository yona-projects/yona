package models.resource;

import models.Project;
import models.enumeration.ResourceType;

public abstract class Resource {
    abstract public Long getId();
    abstract public Project getProject();
    abstract public ResourceType getType();
    public Resource getContainer() { return null; };
    public Long getAuthorId() { return null; };
}
