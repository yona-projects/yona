package playRepository;

import java.io.IOException;

import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.errors.*;

public interface PlayRepository {

    public void create() throws Exception;

    
    public ObjectNode findFileInfo(String path) throws IOException, NoHeadException,
            GitAPIException;

    public byte[] getRawFile(String path) throws MissingObjectException,
            IncorrectObjectTypeException, AmbiguousObjectException, IOException;

    public Object getCore();


    public void delete();

}