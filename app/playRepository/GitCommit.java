package playRepository;

import models.User;

import java.util.Date;
import java.util.Set;
import java.util.TimeZone;

import org.eclipse.jgit.revwalk.RevCommit;

public class GitCommit extends Commit {
    RevCommit revCommit;

    public GitCommit(RevCommit revCommit) {
        this.revCommit = revCommit;
    }

    @Override
    public String getId() {
        return revCommit.getName();
    }

    @Override
    public String getMessage() {
        return revCommit.getFullMessage();
    }

    @Override
    public User getAuthor() {
        return User.findByEmail(getAuthorEmail());
    }

    @Override
    public String getAuthorName() {
        return revCommit.getAuthorIdent().getName();
    }

    @Override
    public String getShortMessage() {
        return revCommit.getShortMessage();
    }

    @Override
    public String getAuthorEmail() {
        return revCommit.getAuthorIdent().getEmailAddress();
    }

    @Override
    public Date getAuthorDate() {
        return revCommit.getAuthorIdent().getWhen();
    }

    @Override
    public TimeZone getAuthorTimezone() {
        return revCommit.getAuthorIdent().getTimeZone();
    }

    @Override
    public String getCommitterName() {
        return revCommit.getCommitterIdent().getName();
    }

    @Override
    public String getCommitterEmail() {
        return revCommit.getCommitterIdent().getEmailAddress();
    }

    @Override
    public Date getCommitterDate() {
        return revCommit.getCommitterIdent().getWhen();
    }

    @Override
    public TimeZone getCommitterTimezone() {
        return revCommit.getCommitterIdent().getTimeZone();
    }

    @Override
    public int getParentCount() {
        return revCommit.getParentCount();
    }

    @Override
    public String getShortId() {
        return revCommit.abbreviate(7).name();
    }
}
