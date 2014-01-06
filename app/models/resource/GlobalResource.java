package models.resource;

import models.Project;
import models.resource.Resource;

abstract public class GlobalResource extends Resource {
    @Override
    public Project getProject() {
        throw new UnsupportedOperationException();
    }
}
