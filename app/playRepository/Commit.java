package playRepository;

import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.resource.Resource;
import utils.AccessControl;
import utils.WatchService;

import java.util.*;

public abstract class Commit {

    public abstract String getShortId();
    public abstract String getId();
    public abstract String getShortMessage();
    public abstract String getMessage();
    public abstract User getAuthor();
    public abstract String getAuthorName();
    public abstract String getAuthorEmail();
    public abstract Date getAuthorDate();
    public abstract TimeZone getAuthorTimezone();
    public abstract String getCommitterName();
    public abstract String getCommitterEmail();
    public abstract Date getCommitterDate();
    public abstract TimeZone getCommitterTimezone();
    public abstract int getParentCount();

    public Set<User> getWatchers(Project project) {
        Set<User> actualWatchers = new HashSet<>();

        // Add the author
        if (!getAuthor().isAnonymous()) {
            actualWatchers.add(getAuthor());
        }

        // Add every user who comments on this commit
        List<CodeComment> comments = CodeComment.find.where()
                .eq("project.id", project.id).eq("commitId", getId()).findList();
        for (CodeComment c : comments) {
            User user = User.find.byId(c.authorId);
            if (user != null) {
                actualWatchers.add(user);
            }
        }

        // Add every user who watches the project to which this commit belongs
        actualWatchers.addAll(WatchService.findWatchers(project.asResource()));

        // For this commit, add every user who watch explicitly and remove who unwatch explicitly.
        actualWatchers.addAll(WatchService.findWatchers(asResource(project)));
        actualWatchers.removeAll(WatchService.findUnwatchers(asResource(project)));

        // Filter the watchers who has no permission to read this commit.
        Set<User> allowedWatchers = new HashSet<>();
        for (User watcher : actualWatchers) {
            if (AccessControl.isAllowed(watcher, project.asResource(), Operation.READ)) {
                allowedWatchers.add(watcher);
            }
        }

        return allowedWatchers;
    }

    public static Project getProjectFromResourceId(String resourceId) {
        String[] pair = resourceId.split(":");
        return Project.find.byId(Long.valueOf(pair[0]));
    }

    public static Resource getAsResource(final String resourceId) {
        return new Resource() {

            @Override
            public String getId() {
                return resourceId;
            }

            @Override
            public Project getProject() {
                return getProjectFromResourceId(resourceId);
            }

            @Override
            public ResourceType getType() {
                return ResourceType.COMMIT;
            }
        };
    }

    public Resource asResource(final Project project) {
        return new Resource() {

            @Override
            public String getId() {
                return project.id + ":" + Commit.this.getId();
            }

            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.COMMIT;
            }
        };
    }
}
