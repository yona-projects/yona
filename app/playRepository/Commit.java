package playRepository;

import java.util.Date;
import java.util.TimeZone;

public interface Commit {
    public String getShortId();
    public String getId();
    public String getShortMessage();
    public String getMessage();
    public String getAuthorName();
    public String getAuthorEmail();
    public Date getAuthorDate();
    public TimeZone getAuthorTimezone();
    public String getCommitterName();
    public String getCommitterEmail();
    public Date getCommitterDate();
    public TimeZone getCommitterTimezone();
    public int getParentCount();
}
