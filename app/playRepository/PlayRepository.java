package playRepository;

import java.io.IOException;
import java.util.List;

import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.*;
import org.tigris.subversion.javahl.ClientException;
import org.tmatesoft.svn.core.SVNException;

public interface PlayRepository {

    public void create() throws IOException, ClientException;


    public ObjectNode findFileInfo(String path) throws IOException, NoHeadException,
            GitAPIException, SVNException;

    public byte[] getRawFile(String path) throws MissingObjectException,
            IncorrectObjectTypeException, AmbiguousObjectException, IOException, SVNException;

    public void delete();

    public String getPatch(String commitId) throws GitAPIException, MissingObjectException,
            IncorrectObjectTypeException, IOException, SVNException;

    public List<Commit> getHistory(int page, int limit, String branch) throws AmbiguousObjectException,
            IOException, NoHeadException, GitAPIException, SVNException;

    public List<String> getBranches();

}