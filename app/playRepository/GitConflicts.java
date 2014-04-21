/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package playRepository;

import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            int[][] conflicts = allConflicts.get(path);
            for (int[] c : conflicts) {
                Conflict conflict = new Conflict();
                conflict.fileName = path;
                for (int j = 0; j < (c.length) - 1; ++j) {
                    if (c[j] >= 0) {
                        ObjectId objectId = mergeResult.getMergedCommits()[j];
                        RevWalk revWalk = new RevWalk(repository);

                        CommitAndLine commitAndLine = new CommitAndLine();
                        try {
                            commitAndLine.gitCommit = new GitCommit(revWalk.parseCommit(objectId));
                            commitAndLine.lineNumber = c[j];
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
