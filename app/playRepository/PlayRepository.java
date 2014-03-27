package playRepository;

import models.resource.Resource;

import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.tigris.subversion.javahl.ClientException;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;
import java.util.List;

/**
 * 코드 저장소를 나타내는 클래스
 *
 * @see {@link GitRepository}
 * @see {@link SVNRepository}
 */
public interface PlayRepository {

    /**
     * 이보다 큰 크기의 파일은 파일 브라우저가 그 내용을 보여주지 않는다.
     */
    long MAX_FILE_SIZE_CAN_BE_VIEWED = play.Configuration.root().getInt(
            "application.codeBrowser.viewer.maxFileSize", 1024 * 1024);

    /**
     * 저장소를 생성한다.
     *
     * @throws IOException
     * @throws ClientException
     */
    public abstract void create() throws IOException, ClientException;

    /**
     * {@code path}가 디렉토리일 경우에는 해당 디렉토리에 들어있는 파일과 디렉토리 목록을 JSON으로 반환하고,
     * 파일일 경우에는 해당 파일 정보를 JSON으로 반환한다.
     *
     * @param path
     * @return
     * @throws IOException
     * @throws GitAPIException
     * @throws SVNException
     */
    public abstract ObjectNode getMetaDataFromPath(String path) throws IOException, GitAPIException, SVNException;

    /**
     * {@code revision}의 {@code path}에 해당하는 파일을 반환한다.
     *
     * @param revision
     * @param path
     * @return
     * @throws IOException
     * @throws SVNException
     */
    public abstract byte[] getRawFile(String revision, String path) throws IOException, SVNException;

    /**
     * 저장소를 삭제한다.
     */
    public abstract void delete();

    /**
     * {@code commitId}에 해당하는 커밋의 변경 내역을 반환한다.
     *
     * @param commitId
     * @return
     * @throws GitAPIException
     * @throws IOException
     * @throws SVNException
     */
    public abstract String getPatch(String commitId) throws IOException, SVNException;

    String getPatch(String revA, String revB) throws IOException, SVNException;

    public abstract List<FileDiff> getDiff(String commitId) throws IOException;

    List<FileDiff> getDiff(String revA, String revB) throws IOException;

    /**
     * {@code untilRevName}에 해당하는 리비전까지의 커밋 목록을 반환한다.
     *
     * @param pageNum 조회할 히스토리 페이지
     * @param pageSize 조회할 히스토리 페이지 당 개수
     * @param untilRev 조회할 커밋 중 가장 최근 커밋을 가리키는 리비전, 이 값이 null이면 HEAD
     * @param path Show only commits that affect the specified path
     * @return
     * @throws IOException
     * @throws GitAPIException
     * @throws SVNException
     */
    public abstract List<Commit> getHistory(int pageNum, int pageSize, String untilRev, String path) throws IOException, GitAPIException, SVNException;

    public abstract Commit getCommit(String rev) throws IOException, SVNException;

    /**
     * 브랜치 목록 조회
     *
     * @return 브랜치 이름 목록
     */
    public abstract List<String> getBranches();

    /**
     * {@code branch}에서 {@code path}가 디렉토리일 경우에는 해당 디렉토리에 들어있는 파일과 디렉토리 목록을 JSON으로 반환하고,
     * 파일일 경우에는 해당 파일 정보를 JSON으로 반환한다.
     *
     * @param branch
     * @param path
     * @return
     * @throws IOException
     * @throws SVNException
     * @throws GitAPIException
     */
    public abstract ObjectNode getMetaDataFromPath(String branch, String path) throws IOException, SVNException, GitAPIException;

    public abstract Resource asResource();

    public abstract boolean isFile(String path) throws SVNException, IOException;

    public abstract boolean isFile(String path, String revStr) throws SVNException, IOException;

    /**
     * 코드저장소 프로젝트명을 변경하고 결과를 반환한다.
     * @param projectName
     * @return 코드저장소 이름 변경성공시 true / 실패시 false
     */
    public abstract boolean renameTo(String projectName);

    /**
     * 기본 브랜치를 반환한다.
     *
     * @return 브랜치 이름
     * @throws IOException
     */
    String getDefaultBranch() throws IOException;

    /**
     * 기본 브랜치를 변경한다.
     *
     * @param target 변경할 대상 브랜치 이름
     * @throws IOException
     */
    void setDefaultBranch(String target) throws IOException;

    /**
     * {@code #commitId}의 부모 커밋이나 부모 리비전에 해당하는 커밋을 반환한다.
     *
     * @param commitId
     * @return
     */
    Commit getParentCommitOf(String commitId);

    /**
     * 저장소가 비어있는지 확인한다.
     *
     * @return 저장소가 비어있을시 true / 비어있지 않을시 false
     */
    boolean isEmpty();

    /**
     * 저장소를 옮긴다.
     *
     * @param fromUserLoginId
     * @param fromProjectName
     * @param toUserLoginId
     * @param toProjectName
     * @return
     */
    boolean move(String fromUserLoginId, String fromProjectName, String toUserLoginId, String toProjectName);
}
