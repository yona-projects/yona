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
