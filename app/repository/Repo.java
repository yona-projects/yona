package repository;

import java.io.IOException;

import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.*;

public interface Repo {

    public abstract void create() throws Exception;

    
    public abstract ObjectNode findFileInfo(String path) throws IOException, NoHeadException,
            GitAPIException;

    public abstract byte[] getRawFile(String path) throws MissingObjectException,
            IncorrectObjectTypeException, AmbiguousObjectException, IOException;

    public abstract Object getCore();

}