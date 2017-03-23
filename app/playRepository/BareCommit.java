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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import utils.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.OverlappingFileLockException;
import java.text.MessageFormat;

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

        ObjectId commitId = null;
        try {
            this.objectInserter = this.repository.newObjectInserter();
            contents = addEOL(changeLineEnding(contents, findFileLineEnding(repository, fileNameWithPath)));
            setHeadObjectId(this.refName);
            commitId = createCommitWithNewTree(createGitObjectWithText(contents));
            RefUpdate.Result result = refUpdate(commitId, refName);
        } catch (OverlappingFileLockException e) {
            play.Logger.error("Overlapping File Lock Error: " + e.getMessage());
        } finally {
            objectInserter.close();
            repository.close();
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

    private RefUpdate.Result refUpdate(ObjectId commitId, String refName) throws IOException {
        RefUpdate ru = this.repository.updateRef(refName);
        ru.setForceUpdate(false);
        ru.setRefLogIdent(getPersonIdent());
        ru.setNewObjectId(commitId);
        if(hasOldCommit(refName)){
            ru.setExpectedOldObjectId(getCurrentMomentHeadObjectId());
        }
        ru.setRefLogMessage(getCommitMessage(), true);

        RefUpdate.Result result = ru.update();
        play.Logger.debug("Online commit: HEAD[" + this.headObjectId + "]:" + result + " New:" + commitId);
        return result;
    }

    private boolean hasOldCommit(String refName) throws IOException {
        return this.repository.findRef(refName).getObjectId() != null;
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
        if(this.repository.findRef(refName).getObjectId() == null){
            this.headObjectId = ObjectId.zeroId();
        } else {
            this.headObjectId = this.repository.findRef(refName).getObjectId();
        }
    }

    public ObjectId getCurrentMomentHeadObjectId() throws IOException {
        if( this.repository.findRef(refName).getObjectId() == null ){
            return ObjectId.zeroId();
        } else {
            return this.repository.findRef(refName).getObjectId();
        }
    }

    public void setRefName(String refName){
        this.refName = refName;
    }

    // Bare commit. It is referenced from https://gist.github.com/porcelli/3882505
    public ObjectId commitTextFile(final String branchName, final String path, String text, final String message) throws IOException {
        this.file = new File(this.repository.getDirectory(), path);
        org.apache.commons.io.FileUtils.writeStringToFile(this.file, text, "UTF-8");

        ObjectId commitId = null;
        Git git = new Git(this.repository);
        try (final ObjectInserter inserter = git.getRepository().newObjectInserter()) {
                // Create the in-memory index of the new/updated issue.
                this.headObjectId = git.getRepository().resolve(this.refName + "^{commit}");
                final DirCache index = createTemporaryIndex(git, this.headObjectId, path, file);
                final ObjectId indexTreeId = index.writeTree(inserter);

                // Create a commit object
                final CommitBuilder commit = getCommitBuilder(message, indexTreeId);

                // Insert the commit into the repository
                commitId = inserter.insert(commit);
                inserter.flush();

                final RefUpdate ru = getRefUpdate(branchName, commitId, git);
                final RefUpdate.Result rc = ru.forceUpdate();
                switch (rc) {
                    case NEW:
                    case FORCED:
                    case FAST_FORWARD:
                        break;
                    case REJECTED:
                    case LOCK_FAILURE:
                        throw new ConcurrentRefUpdateException(JGitText.get().couldNotLockHEAD, ru.getRef(), rc);
                    default:
                        throw new JGitInternalException(MessageFormat.format(JGitText.get().updatingRefFailed, Constants.HEAD, commitId.toString(), rc));
                }
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }

        return commitId;
    }

    private RefUpdate getRefUpdate(String branchName, ObjectId commitId, Git git) throws IOException {
        final RevWalk revWalk = new RevWalk(git.getRepository());
        final RevCommit revCommit = revWalk.parseCommit(commitId);
        final RefUpdate ru = git.getRepository().updateRef("refs/heads/" + branchName);
        if (this.headObjectId == null) {
            ru.setExpectedOldObjectId(ObjectId.zeroId());
        } else {
            ru.setExpectedOldObjectId(this.headObjectId);
        }
        ru.setNewObjectId(commitId);
        ru.setRefLogMessage("commit: " + revCommit.getShortMessage(), false);
        revWalk.close();
        return ru;
    }

    private CommitBuilder getCommitBuilder(String message, ObjectId indexTreeId) {
        final CommitBuilder commit = new CommitBuilder();
        commit.setAuthor(this.getPersonIdent());
        commit.setCommitter(this.getPersonIdent());
        commit.setEncoding(Constants.CHARACTER_ENCODING);
        commit.setMessage(message);
        //headId can be null if the repository has no commit yet
        if (this.headObjectId != null) {
            commit.setParentId(this.headObjectId);
        }
        commit.setTreeId(indexTreeId);
        return commit;
    }

    private static DirCache createTemporaryIndex(final Git git, final ObjectId headId, final String path, final File file) {
        final DirCache inCoreIndex = DirCache.newInCore();
        final DirCacheBuilder dcBuilder = inCoreIndex.builder();
        final ObjectInserter inserter = git.getRepository().newObjectInserter();

        try {
            if (file != null) {
                final DirCacheEntry dcEntry = new DirCacheEntry(path);
                dcEntry.setLength(file.length());
                dcEntry.setLastModified(file.lastModified());
                dcEntry.setFileMode(FileMode.REGULAR_FILE);

                final InputStream inputStream = new FileInputStream(file);
                try {
                    dcEntry.setObjectId(inserter.insert(Constants.OBJ_BLOB, file.length(), inputStream));
                } finally {
                    inputStream.close();
                }

                dcBuilder.add(dcEntry);
            }

            if (headId != null) {
                final TreeWalk treeWalk = new TreeWalk(git.getRepository());
                final int hIdx = treeWalk.addTree(new RevWalk(git.getRepository()).parseTree(headId));
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    final String walkPath = treeWalk.getPathString();
                    final CanonicalTreeParser hTree = treeWalk.getTree(hIdx, CanonicalTreeParser.class);

                    if (!walkPath.equals(path)) {
                        // add entries from HEAD for all other paths
                        // create a new DirCacheEntry with data retrieved from HEAD
                        final DirCacheEntry dcEntry = new DirCacheEntry(walkPath);
                        dcEntry.setObjectId(hTree.getEntryObjectId());
                        dcEntry.setFileMode(hTree.getEntryFileMode());

                        // add to temporary in-core index
                        dcBuilder.add(dcEntry);
                    }
                }
                treeWalk.close();
            }

            dcBuilder.finish();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            inserter.close();
        }

        if (file == null) {
            final DirCacheEditor editor = inCoreIndex.editor();
            editor.add(new DirCacheEditor.DeleteTree(path));
            editor.finish();
        }

        return inCoreIndex;
    }
}
