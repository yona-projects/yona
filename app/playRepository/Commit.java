package playRepository;

import models.CodeComment;
import models.CommitExplicitWatching;
import models.Project;
import models.User;
import models.enumeration.Operation;
import play.db.ebean.Model;
import utils.AccessControl;

import javax.persistence.*;
import java.beans.Transient;
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

        // Add every user who watch the project to which this commit belongs
        actualWatchers.addAll(project.watchers);

        // For this commit, add every user who watch explicitly and remove who unwatch explicitly.
        CommitExplicitWatching explicit =
                CommitExplicitWatching.findByProjectAndCommitId(project, getId());
        if (explicit != null) {
            actualWatchers.addAll(explicit.watchers);
            actualWatchers.removeAll(explicit.unwatchers);
        }

        // Filter the watchers who has no permission to read this commit.
        Set<User> allowedWatchers = new HashSet<>();
        for (User watcher : actualWatchers) {
            if (AccessControl.isAllowed(watcher, project.asResource(), Operation.READ)) {
                allowedWatchers.add(watcher);
            }
        }

        return allowedWatchers;
    }

    public void watch(Project project, User user) {
        CommitExplicitWatching explicit = CommitExplicitWatching.getOrCreate(project, getId());
        explicit.watchers.add(user);
        explicit.update();
    }

    public void unwatch(Project project, User user) {
        CommitExplicitWatching explicit = CommitExplicitWatching.getOrCreate(project, getId());
        explicit.unwatchers.add(user);
        explicit.update();
    }
}
