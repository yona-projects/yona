package models.resource;

import models.Project;
import models.enumeration.ResourceType;
import models.resource.Resource;

abstract public class GlobalResource extends Resource {
    @Override
    public Project getProject() {
        throw new UnsupportedOperationException();
    }
}
