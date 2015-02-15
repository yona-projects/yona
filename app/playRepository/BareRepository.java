/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Suwon Chae
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

import models.Project;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.OrTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS;
import utils.LineEnding.EndingType;

import javax.servlet.ServletException;
import java.io.IOException;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static utils.LineEnding.findLineEnding;

public class BareRepository {
    /**
     * read project README file with readme filename filter from repository
     *
     * @param project
     * @return
     */
    public static String readREADME(Project project){
        Repository repository;
        ObjectLoader loader = null;
        repository = getRepository(project);
        try {
            loader = repository.open(getFirstFoundREADMEfileObjectId(repository));
        } catch (IOException e) {
            e.printStackTrace();
            play.Logger.error(e.getMessage());
        }
        if (loader == null) {
            return null;
        }
        return new String(loader.getCachedBytes(), utils.Config.getCharset());
    }

    public static Repository getRepository(Project project){
        Repository repository = null;
        try {
            RepositoryCache.FileKey fileKey = RepositoryCache.FileKey.exact(
                    RepositoryService.getRepository(project).getDirectory(), FS.DETECTED);
            repository = fileKey.open(false);
        } catch (ServletException | IOException e) {
            e.printStackTrace();
            play.Logger.error(e.getMessage());
        }
        return repository;
    }

    public static ObjectId getFileObjectId(Repository repository, String fileNameWithPath) throws IOException {
        TreeWalk treeWalk = new TreeWalk(repository);
        RevTree revTree = getRevTreeFromRef(repository, repository.getRef(HEAD));
        if( revTree == null ){
            return ObjectId.zeroId();
        }
        treeWalk.addTree(revTree);
        treeWalk.setRecursive(false);
        treeWalk.setFilter(PathFilter.create(fileNameWithPath));
        return treeWalk.getObjectId(0);
    }

    private static ObjectId getFirstFoundREADMEfileObjectId(Repository repository) throws IOException {
        TreeWalk treeWalk = new TreeWalk(repository);
        RevTree revTree = getRevTreeFromRef(repository, repository.getRef(HEAD));
        if( revTree == null ){
            return ObjectId.zeroId();
        }
        treeWalk.addTree(revTree);
        treeWalk.setRecursive(false);
        treeWalk.setFilter(OrTreeFilter.create(READMEFileNameFilter()));

        if (!treeWalk.next()) {
            play.Logger.info("No tree or no README file found at " + repository.getDirectory());
        }
        return treeWalk.getObjectId(0);
    }

    private static RevTree getRevTreeFromRef(Repository repository, Ref ref) throws IOException {
        if(ref.getObjectId() == null){
            return null;
        }
        RevWalk revWalk = new RevWalk(repository);
        RevCommit commit = revWalk.parseCommit(ref.getObjectId());
        return commit.getTree();
    }

    private static TreeFilter[] READMEFileNameFilter() {
        TreeFilter[] filters = new TreeFilter[4];
        filters[0] = PathFilter.create("README.md");
        filters[1] = PathFilter.create("readme.md");
        filters[2] = PathFilter.create("README.markdown");
        filters[3] = PathFilter.create("readme.markdown");
        return filters;
    }

    public static EndingType findFileLineEnding(Repository repository, String fileNameWithPath) throws IOException {
        ObjectId oldObjectId = BareRepository.getFileObjectId(repository, fileNameWithPath);
        if(oldObjectId.equals(ObjectId.zeroId())){
            return EndingType.UNDEFINED;
        } else {
            String fileContents = new String(repository.open(oldObjectId).getBytes(), utils.Config.getCharset());
            return findLineEnding(fileContents);
        }
    }
}
