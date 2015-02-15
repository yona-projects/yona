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
import models.User;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import utils.Config;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static playRepository.BareRepository.findFileLineEnding;
import static utils.LineEnding.addEOL;
import static utils.LineEnding.changeLineEnding;

public class BareCommit {
    private PersonIdent personIdent;
    private Repository repository;
    private String commitMessage;
    private ObjectInserter objectInserter;

    private File file;
    private String refName;
    private ObjectId headObjectId;

    /**
     * Constructs a BareCommit.
     *
     * The given project MUST have a Git repository which has been CREATED.
     *
     * @param project  a project whose repository to be commited
     * @param user     the author and also the committer
     */
    public BareCommit(Project project, User user) {
        this.repository = BareRepository.getRepository(project);
        this.personIdent = new PersonIdent(user.name, user.email);
    }

    /**
     * Commit to bare repository with filename and text contents
     *
     * @param fileNameWithPath
     * @param contents
     * @param message
     */
    public ObjectId commitTextFile(
            String fileNameWithPath, String contents, String message) throws IOException {
        this.file = new File(fileNameWithPath);
        setCommitMessage(message);
        if (this.refName == null) {
            setRefName(HEAD);
        }

        RefHeadFileLock refHeadFileLock = new RefHeadFileLock().invoke(this.refName);
        ObjectId commitId = null;
        try {
            this.objectInserter = this.repository.newObjectInserter();
            contents = addEOL(changeLineEnding(contents, findFileLineEnding(repository, fileNameWithPath)));
            commitId = createCommitWithNewTree(createGitObjectWithText(contents));
            refUpdate(commitId, refName);
        } catch (OverlappingFileLockException e) {
            play.Logger.error("Overlapping File Lock Error: " + e.getMessage());
        } finally {
            objectInserter.release();
            repository.close();
            refHeadFileLock.release();
        }

        return commitId;
    }

    private boolean noHeadRef() {
        if(this.headObjectId == null) {
            return true;
        }
        return this.headObjectId.equals(ObjectId.zeroId());
    }

    private ObjectId createCommitWithNewTree(ObjectId targetTextFileObjectId) throws IOException {
        return objectInserter.insert(buildCommitWith(file.getName(), targetTextFileObjectId));
    }

    private CommitBuilder buildCommitWith(String fileName, ObjectId fileObjectId) throws IOException {
        CommitBuilder commit = new CommitBuilder();
        commit.setTreeId(createTreeWith(fileName, fileObjectId));
        if (!noHeadRef()) {
            commit.setParentId(this.headObjectId);
        }
        commit.setAuthor(getPersonIdent());
        commit.setCommitter(getPersonIdent());
        commit.setMessage(getCommitMessage());
        return commit;
    }

    private ObjectId createTreeWith(String fileName, ObjectId fileObjectId) throws IOException {
        if (noHeadRef()){
            return objectInserter.insert(newTreeWith(fileName, fileObjectId));
        } else {
            return objectInserter.insert(rebuildExistingTreeWith(fileName, fileObjectId));
        }
    }

    private TreeFormatter newTreeWith(String fileName, ObjectId fileObjectId) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.append(fileName, FileMode.REGULAR_FILE, fileObjectId);
        return formatter;
    }

    private TreeFormatter rebuildExistingTreeWith(String fileName, ObjectId fileObjectId) throws IOException {
        TreeFormatter formatter = new TreeFormatter();
        CanonicalTreeParser treeParser = getCanonicalTreeParser(this.repository);

        boolean isInsertedInTree = false;
        while(!treeParser.eof()){
            String entryName = new String(treeParser.getEntryPathBuffer(), 0, treeParser.getEntryPathLength(), Config.getCharset());
            String nameForComparison = entryName;

            if (treeParser.getEntryFileMode() == FileMode.TREE){
                nameForComparison = entryName.concat("/"); //for tree ordering comparison
            }
            if (nameForComparison.compareTo(fileName) == 0 && isInsertedInTree == false){
                formatter.append(fileName, FileMode.REGULAR_FILE, fileObjectId);
                isInsertedInTree = true;
            } else if (nameForComparison.compareTo(fileName) > 0 && isInsertedInTree == false) {
                formatter.append(fileName, FileMode.REGULAR_FILE, fileObjectId);
                formatter.append(entryName.getBytes(Config.getCharset())
                        , treeParser.getEntryFileMode()
                        , treeParser.getEntryObjectId());
                isInsertedInTree = true;
            } else {
                formatter.append(entryName.getBytes(Config.getCharset())
                        , treeParser.getEntryFileMode()
                        , treeParser.getEntryObjectId());
            }

            treeParser = treeParser.next();
        }
        if(!isInsertedInTree){
            formatter.append(fileName, FileMode.REGULAR_FILE, fileObjectId);
        }
        return formatter;
    }

    private CanonicalTreeParser getCanonicalTreeParser(Repository repository) throws IOException {
        RevWalk revWalk = new RevWalk(repository);
        RevCommit commit = revWalk.parseCommit(this.headObjectId);
        return new CanonicalTreeParser(new byte[]{}, revWalk.getObjectReader(), commit.getTree().getId());
    }

    private ObjectId createGitObjectWithText(String contents) throws IOException {
        byte[] bytes = contents.getBytes(Config.getCharset());
        return objectInserter.insert(OBJ_BLOB, bytes, 0, bytes.length);
    }

    private void refUpdate(ObjectId commitId, String refName) throws IOException {
        RefUpdate ru = this.repository.updateRef(refName);
        ru.setForceUpdate(false);
        ru.setRefLogIdent(getPersonIdent());
        ru.setNewObjectId(commitId);
        if(hasOldCommit(refName)){
            ru.setExpectedOldObjectId(getCurrentMomentHeadObjectId());
        }
        ru.setRefLogMessage(getCommitMessage(), false);
        ru.update();
    }

    private boolean hasOldCommit(String refName) throws IOException {
        return this.repository.getRef(refName).getObjectId() != null;
    }

    private PersonIdent getPersonIdent() {
        if (this.personIdent == null) {
            this.personIdent = new PersonIdent(this.repository);
        }
        return personIdent;
    }

    private String getCommitMessage() {
        if(this.commitMessage == null){
            return "Update " + this.file.getName();
        }
        return commitMessage;
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }

    public void setHeadObjectId(String refName) throws IOException {
        if(this.repository.getRef(refName).getObjectId() == null){
            this.headObjectId = ObjectId.zeroId();
        } else {
            this.headObjectId = this.repository.getRef(refName).getObjectId();
        }
    }

    public ObjectId getCurrentMomentHeadObjectId() throws IOException {
        if( this.repository.getRef(refName).getObjectId() == null ){
            return ObjectId.zeroId();
        } else {
            return this.repository.getRef(refName).getObjectId();
        }
    }

    public void setRefName(String refName){
        this.refName = refName;
    }

    private class RefHeadFileLock {
        private FileChannel channel;
        private FileLock lock;
        private File refHeadFile;

        public RefHeadFileLock invoke(String refName) throws IOException {
            if (!repository.getDirectory().exists()) {
                throw new IllegalStateException("The repository seems not to be created");
            }

            refHeadFile = new File(repository.getDirectory().getPath(),
                    repository.getRef(refName).getLeaf().getName());
            if(refHeadFile.exists()){
                channel = new RandomAccessFile(refHeadFile, "rw").getChannel();
                lock = channel.lock();
            }
            setHeadObjectId(refName);
            return this;
        }

        public void release() {
            try {
                if(refHeadFile.exists()) {
                    if(lock != null) lock.release();
                    if(channel != null) channel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                play.Logger.error(e.getMessage());
            }
        }
    }
}
