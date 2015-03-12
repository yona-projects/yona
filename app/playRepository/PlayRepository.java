/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Ahn Hyeok Jun
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

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.resource.Resource;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface PlayRepository {

    public final long MAX_FILE_SIZE_CAN_BE_VIEWED = play.Configuration.root().getInt(
            "application.codeBrowser.viewer.maxFileSize", 1024 * 1024);

    public abstract void create() throws IOException, SVNException;

    public abstract boolean isIntermediateFolder(String path);

    public abstract ObjectNode getMetaDataFromPath(String path) throws IOException, GitAPIException, SVNException;

    public abstract byte[] getRawFile(String revision, String path) throws IOException, SVNException;

    public abstract void delete() throws Exception;

    public abstract String getPatch(String commitId) throws IOException, SVNException;

    String getPatch(String revA, String revB) throws IOException, SVNException;

    public abstract List<FileDiff> getDiff(String commitId) throws IOException;

    List<FileDiff> getDiff(String revA, String revB) throws IOException;

    public abstract List<Commit> getHistory(int pageNum, int pageSize, String untilRev, String path) throws IOException, GitAPIException, SVNException;

    public abstract Commit getCommit(String rev) throws IOException, SVNException;

    public abstract List<String> getRefNames();

    public abstract ObjectNode getMetaDataFromPath(String branch, String path) throws IOException, SVNException, GitAPIException;

    public abstract Resource asResource();

    public abstract boolean isFile(String path) throws SVNException, IOException;

    public abstract boolean isFile(String path, String revStr) throws SVNException, IOException;

    public abstract boolean renameTo(String projectName);

    String getDefaultBranch() throws IOException;

    void setDefaultBranch(String target) throws IOException;

    Commit getParentCommitOf(String commitId);

    boolean isEmpty();

    boolean move(String srcProjectOwner, String srcProjectName, String desrProjectOwner, String destProjectName);

    public File getDirectory();
}
