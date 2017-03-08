/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package utils;

import controllers.UserApp;
import models.Project;
import org.eclipse.jgit.lib.ObjectId;
import playRepository.BareCommit;
import playRepository.RepositoryService;

import java.io.IOException;

public class GitUtil {
    public static String getReadTextFile(Project project, String branchName, String filenameWithPath) {
        try {
            byte[] bytes = RepositoryService.getRepository(project)
                    .getRawFile(branchName, filenameWithPath);
            return new String(bytes, FileUtil.detectCharset(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized static void commitTextFile(Project project, String branchName, String path, String text, String msg) {
        BareCommit bare = new BareCommit(project, UserApp.currentUser());
        bare.setRefName(branchName);
        ObjectId objectId = null;
        try {
            objectId = bare.commitTextFile(branchName, path, text, msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
        play.Logger.debug("Online Commit: " + project.name + ":" + path + ":" + objectId);
    }

}
