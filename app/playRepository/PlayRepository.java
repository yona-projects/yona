package playRepository;

import java.io.IOException;

import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.*;
import org.tigris.subversion.javahl.ClientException;

public interface PlayRepository {

    public void create() throws IOException, ClientException;

    
    public ObjectNode findFileInfo(String path) throws IOException, NoHeadException,
            GitAPIException;

    public byte[] getRawFile(String path) throws MissingObjectException,
            IncorrectObjectTypeException, AmbiguousObjectException, IOException;

    public Object getCore();


    public void delete();

}