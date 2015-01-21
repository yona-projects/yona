/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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

import models.User;

import java.util.Date;
import java.util.TimeZone;

import org.tmatesoft.svn.core.SVNLogEntry;

public class SvnCommit extends Commit {
    SVNLogEntry entry;

    public SvnCommit(SVNLogEntry entry) {
        this.entry = entry;
    }

    private long getIdAsLong() {
        return entry.getRevision();
    }

    @Override
    public String getMessage() {
        return entry.getMessage();
    }

    @Override
    public User getAuthor() {
        return User.findByLoginId(getAuthorName());
    }

    @Override
    public String getAuthorName() {
        return entry.getAuthor();
    }

    @Override
    public String getId() {
        return String.valueOf(getIdAsLong());
    }

    @Override
    public String getShortMessage() {
        String message = getMessage();
        if (message != null && !message.isEmpty()) {
            String[] lines = message.trim().split("\n");
            return lines.length > 0 ? lines[0] : "";
        } else {
            return "";
        }
    }

    @Override
    public String getAuthorEmail() {
        return null;
    }

    @Override
    public Date getAuthorDate() {
        return entry.getDate();
    }

    @Override
    public TimeZone getAuthorTimezone() {
        return null;
    }

    @Override
    public String getCommitterName() {
        return getAuthorName();
    }

    @Override
    public String getCommitterEmail() {
        return null;
    }

    @Override
    public Date getCommitterDate() {
        return getAuthorDate();
    }

    @Override
    public TimeZone getCommitterTimezone() {
        return null;
    }

    @Override
    public int getParentCount() {
        if (getIdAsLong() > 1) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public String getShortId() {
        return getId();
    }
}
