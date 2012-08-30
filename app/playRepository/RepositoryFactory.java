package playRepository;

import models.Project;

public class RepositoryFactory {
    public static PlayRepository getRepository(Project project) throws Exception{
        if(project.vcs.equals(RepositoryService.VCS_GIT)) {
            return new GitRepository(project.owner, project.name);
        } else if(project.vcs.equals(RepositoryService.VCS_SUBVERSION)) {
            return new SVNRepository(project.owner, project.name);
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
