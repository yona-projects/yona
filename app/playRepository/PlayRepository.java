package playRepository;

import java.io.IOException;
import java.util.List;

import models.resource.Resource;

import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.*;
import org.tigris.subversion.javahl.ClientException;
import org.tmatesoft.svn.core.SVNException;

public interface PlayRepository {

    public abstract void create() throws IOException, ClientException;

    public abstract ObjectNode findFileInfo(String path) throws IOException, NoHeadException,
            GitAPIException, SVNException;

    public abstract byte[] getRawFile(String path) throws MissingObjectException,
            IncorrectObjectTypeException, AmbiguousObjectException, IOException, SVNException;

    public abstract void delete();

    public abstract String getPatch(String commitId) throws GitAPIException, MissingObjectException,
            IncorrectObjectTypeException, IOException, SVNException;

    public abstract List<Commit> getHistory(int page, int limit, String branch) throws AmbiguousObjectException,
            IOException, NoHeadException, GitAPIException, SVNException;

    public abstract List<String> getBranches();


    public abstract ObjectNode findFileInfo(String branch, String path) throws AmbiguousObjectException, IOException, SVNException, NoHeadException, GitAPIException;

    public abstract Resource asResource();
}