package playRepository;

import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Keesun Baik
 */
public class GitConflicts {

    /**
     * conflict가 발생한 파일 목록
     */
    public List<String> conflictFiles = new ArrayList<>();

    /**
     * conflict 목록
     *
     * conflict가 발생한 파일 한개는 여러개의 conflict를 가지고 있을 수 있다.
     */
    public List<Conflict> conflictDetails = new ArrayList<>();

    /**
     * MergeResult에서 #conflictFiles 데이터와 #conflicts 데이터를 뽑아낸다.
     *
     * @param repository
     * @param mergeResult
     * @see http://download.eclipse.org/jgit/docs/latest/apidocs/org/eclipse/jgit/api/MergeResult.html#getConflicts()
     */
    public GitConflicts(Repository repository, MergeResult mergeResult) {
        Map<String, int[][]> allConflicts = mergeResult.getConflicts();
        for (String path : allConflicts.keySet()) {
            conflictFiles.add(path);
            int[][] c = allConflicts.get(path);
            for (int i = 0; i < c.length; ++i) {
                Conflict conflict = new Conflict();
                conflict.fileName = path;
                for (int j = 0; j < (c[i].length) - 1; ++j) {
                    if (c[i][j] >= 0) {
                        ObjectId objectId = mergeResult.getMergedCommits()[j];
                        RevWalk revWalk = new RevWalk(repository);

                        CommitAndLine commitAndLine = new CommitAndLine();
                        try {
                            commitAndLine.gitCommit = new GitCommit(revWalk.parseCommit(objectId));
                            commitAndLine.lineNumber = c[i][j];
                            conflict.commitAndLines.add(commitAndLine);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                conflictDetails.add(conflict);
            }
        }

    }

    public static class Conflict {

        public String fileName;

        public List<CommitAndLine> commitAndLines = new ArrayList<>();

    }

    public static class CommitAndLine {

        public GitCommit gitCommit;

        public int lineNumber;

    }

}
