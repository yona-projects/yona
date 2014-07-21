/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
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

package models;

import com.github.zafarkhaja.semver.Version;
import com.github.zafarkhaja.semver.util.UnexpectedElementTypeException;
import com.typesafe.config.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.lib.Ref;
import play.Configuration;
import play.Logger;
import play.libs.Akka;
import scala.concurrent.duration.Duration;
import utils.Config;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class YobiUpdate {
    private static final String UPDATE_REPOSITORY_URL = Configuration.root()
            .getString("application.update.repositoryUrl", "http://demo.yobi.io/naver/Yobi");
    private static final String RELEASE_URL_FORMAT = Configuration.root()
            .getString("application.update.releaseUrlFormat",
                    "https://github.com/naver/yobi/releases/tag/v%s");

    public static String getReleaseUrl(String version) throws GitAPIException {
        return String.format(RELEASE_URL_FORMAT, version);
    }

    /**
     * Fetch the latest version to update.
     *
     * @Return  a string to represent the version to update; null if there is
     *          no version to update
     */
    public static String fetchVersionToUpdate() throws GitAPIException {
        Version current = Version.valueOf(Config.semverize(Config.getCurrentVersion()));
        Version latest = current;
        boolean isUpdateRequired = false;

        Collection<Ref> refs;

        refs = Git.lsRemoteRepository()
                .setRemote(UPDATE_REPOSITORY_URL)
                .setHeads(false)
                .setTags(true)
                .call();

        for(Ref ref : refs) {
            String tag = ref.getName().replaceFirst("^refs/tags/", "");
            if (tag.charAt(0) == 'v') {
                String versionString = Config.semverize(tag);

                try {
                    Version ver = Version.valueOf(versionString);
                    if (ver.greaterThan(latest)) {
                        isUpdateRequired = true;
                        latest = ver;
                    }
                } catch (UnexpectedElementTypeException e) {
                    play.Logger.warn("Failed to parse a version: " +
                            versionString);
                }
            }
        }

        if (isUpdateRequired) {
            return latest.toString();
        } else {
            return null;
        }
    }
}
