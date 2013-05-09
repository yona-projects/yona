package playRepository;

import models.resource.Resource;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.tigris.subversion.javahl.ClientException;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;
import java.util.List;

public interface PlayRepository {

    public abstract void create() throws IOException, ClientException;

    public abstract ObjectNode findFileInfo(String path) throws IOException, GitAPIException, SVNException;

    public abstract byte[] getRawFile(String path) throws IOException, SVNException;

    public abstract void delete();

    public abstract String getPatch(String commitId) throws GitAPIException, IOException, SVNException;

    public abstract List<Commit> getHistory(int page, int limit, String branch) throws IOException, GitAPIException, SVNException;

    public abstract List<String> getBranches();

    public abstract ObjectNode findFileInfo(String branch, String path) throws IOException, SVNException, GitAPIException;

    public abstract Resource asResource();
}