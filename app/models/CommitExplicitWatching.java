package models;

import com.avaje.ebean.validation.NotNull;
import controllers.CodeHistoryApp;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.Set;

@Entity
public class CommitExplicitWatching extends Model {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @ManyToOne
    public Project project;

    @NotNull
    public String commitId;

    @ManyToMany
    @JoinTable(name="commit_explicit_watcher")
    public Set<User> watchers;

    @ManyToMany
    @JoinTable(name="commit_explicit_unwatcher")
    public Set<User> unwatchers;

    public static Finder<Long, CommitExplicitWatching> find = new Finder<>(Long.class,
            CommitExplicitWatching.class);

    public static CommitExplicitWatching getOrCreate(Project project, String commitId) {
        CommitExplicitWatching watching = findByProjectAndCommitId(project, commitId);

        if (watching == null) {
            watching = create(project, commitId);
        }

        return watching;
    }

    public static CommitExplicitWatching create(Project project, String commitId) {
        CommitExplicitWatching watching = new CommitExplicitWatching();
        watching.commitId = commitId;
        watching.project = project;
        watching.save();

        return watching;
    }

    public static CommitExplicitWatching findByProjectAndCommitId(Project project,
                                                                  String commitId) {
        return find.where().eq("project.id", project.id).eq("commitId", commitId).findUnique();
    }
}
