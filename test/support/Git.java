/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
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
package support;

import org.eclipse.jgit.api.errors.GitAPIException;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import playRepository.GitRepository;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Git {
    public static RevCommit commit(Repository repository, String fileName,
                                   String contents, String commitMessage) throws IOException, GitAPIException {
        String wcPath = repository.getWorkTree().getAbsolutePath();
        org.eclipse.jgit.api.Git git = new org.eclipse.jgit.api.Git(repository);
        BufferedWriter out = new BufferedWriter(new FileWriter(wcPath + "/" + fileName));
        out.write(contents);
        out.flush();
        git.add().addFilepattern(fileName).call();
        return git.commit().setMessage(commitMessage).call();
    }

    public static Repository createRepository(String userName, String projectName, boolean bare) throws IOException {
        String wcPath = GitRepository.getRepoPrefix() + userName + "/" + projectName;
        String repoPath = wcPath + "/.git";
        File repoDir = new File(repoPath);
        Repository repository = new RepositoryBuilder().setGitDir(repoDir).build();
        repository.create(bare);
        return repository;
    }
}
