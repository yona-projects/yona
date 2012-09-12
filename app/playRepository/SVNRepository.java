package playRepository;

import java.io.File;

import javax.servlet.*;

import org.codehaus.jackson.node.ObjectNode;
import org.tigris.subversion.javahl.ClientException;

import utils.FileUtil;


public class SVNRepository implements PlayRepository {
    public static final String REPO_PREFIX = "repo/svn/";

    private String projectName;

    private String userName;

    public SVNRepository(final String userName, String projectName) throws ServletException {
        this.userName = userName;
        this.projectName = projectName;
    }


    public byte[] getRawFile(String path) {
        return null;

    }

    public ObjectNode findFileInfo(String path) {
        return null;
    }

    @Override
    public void create() throws ClientException {
        String svnPath = new File(SVNRepository.REPO_PREFIX + userName + "/" + projectName)
                .getAbsolutePath();
        new org.tigris.subversion.javahl.SVNAdmin().create(svnPath, false, false, null, "fsfs");
    }

    @Override
    public void delete() {
        FileUtil.rm_rf(new File(REPO_PREFIX + userName + "/" + projectName));
    }
    
}
