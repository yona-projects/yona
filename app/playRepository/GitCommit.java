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
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Date;
import java.util.TimeZone;

public class GitCommit extends Commit {
    final private RevCommit revCommit;

    private PersonIdent authorIdent;

    private PersonIdent committerIdent;

    private String fullMessage;

    private String shortMessage;

    public GitCommit(RevCommit revCommit) {
        this.revCommit = revCommit;
    }

    @Override
    public String getId() {
        return revCommit.getName();
    }

    // Imported from getFullMessage of
    // https://github.com/eclipse/jgit/blob/v3.0.0.201305080800-m7/org.eclipse.jgit/src/org/eclipse/jgit/revwalk/RevCommit.java
    // and modified by Yi EungJun
    /**
     * Parse the complete commit message and decode it to a string.
     * <p>
     * This method parses and returns the message portion of the commit buffer,
     * after taking the commit's character set into account and decoding the
     * buffer using that character set. This method is a fairly expensive
     * operation and produces a new string on each invocation.
     *
     * @return decoded commit message as a string. Never null.
     */
    @Override
    public String getMessage() {
        if (fullMessage == null) {
            final byte[] raw = revCommit.getRawBuffer();
            final int msgB = RawParseUtils.commitMessage(raw, 0);
            if (msgB < 0)
                return ""; //$NON-NLS-1$
            final Charset enc = parseEncoding(raw, Charset.defaultCharset());
            fullMessage = RawParseUtils.decode(enc, raw, msgB, raw.length);
        }

        return fullMessage;
    }

    @Override
    public User getAuthor() {
        return User.findByEmail(getAuthorEmail());
    }

    @Override
    public String getAuthorName() {
        return getAuthorIdent().getName();
    }

    // Imported from
    // https://github.com/eclipse/jgit/blob/v3.0.0.201305080800-m7/org.eclipse.jgit/src/org/eclipse/jgit/revwalk/RevCommit.java
    // and modified by Yi EungJun
    /**
     * Parse the commit message and return the first "line" of it.
     * <p>
     * The first line is everything up to the first pair of LFs. This is the
     * "oneline" format, suitable for output in a single line display.
     * <p>
     * This method parses and returns the message portion of the commit buffer,
     * after taking the commit's character set into account and decoding the
     * buffer using that character set. This method is a fairly expensive
     * operation and produces a new string on each invocation.
     *
     * @return decoded commit message as a string. Never null. The returned
     *         string does not contain any LFs, even if the first paragraph
     *         spanned multiple lines. Embedded LFs are converted to spaces.
     */
    @Override
    public String getShortMessage() {
        if (shortMessage == null) {
            final byte[] raw = revCommit.getRawBuffer();
            final int msgB = RawParseUtils.commitMessage(raw, 0);
            if (msgB < 0)
                return ""; //$NON-NLS-1$

            final Charset enc = parseEncoding(raw, Charset.defaultCharset());
            final int msgE = RawParseUtils.endOfParagraph(raw, msgB);
            String str = RawParseUtils.decode(enc, raw, msgB, msgE);
            if (hasLF(raw, msgB, msgE))
                str = StringUtils.replaceLineBreaksWithSpace(str);
            shortMessage = str;
        }

        return shortMessage;
    }

    @Override
    public String getAuthorEmail() {
        return getAuthorIdent().getEmailAddress();
    }

    @Override
    public Date getAuthorDate() {
        return getAuthorIdent().getWhen();
    }

    @Override
    public TimeZone getAuthorTimezone() {
        return getAuthorIdent().getTimeZone();
    }

    @Override
    public String getCommitterName() {
        return getCommitterIdent().getName();
    }

    @Override
    public String getCommitterEmail() {
        return getCommitterIdent().getEmailAddress();
    }

    @Override
    public Date getCommitterDate() {
        return getCommitterIdent().getWhen();
    }

    public long getCommitTime() {
        return revCommit.getCommitTime();
    }

    @Override
    public TimeZone getCommitterTimezone() {
        return getCommitterIdent().getTimeZone();
    }

    @Override
    public int getParentCount() {
        return revCommit.getParentCount();
    }

    @Override
    public String getShortId() {
        return revCommit.abbreviate(7).name();
    }

    // Imported from
    // https://github.com/eclipse/jgit/blob/v3.0.0.201305080800-m7/org.eclipse.jgit/src/org/eclipse/jgit/revwalk/RevCommit.java
    // and modified by Yi EungJun
    /**
     * Parse the author identity from the raw buffer.
     * <p>
     * This method parses and returns the content of the author line, after
     * taking the commit's character set into account and decoding the author
     * name and email address. This method is fairly expensive and produces a
     * new PersonIdent instance on each invocation. Callers should invoke this
     * method only if they are certain they will be outputting the result, and
     * should cache the return value for as long as necessary to use all
     * information from it.
     * <p>
     * RevFilter implementations should try to use {@link RawParseUtils} to scan
     * the {@link RevCommit#getRawBuffer()} instead, as this will allow faster evaluation
     * of commits.
     *
     * @return identity of the author (name, email) and the time the commit was
     *         made by the author; null if no author line was found.
     */
    public final PersonIdent getAuthorIdent() {
        if (authorIdent == null) {
            final byte[] raw = revCommit.getRawBuffer();
            final int nameB = RawParseUtils.author(raw, 0);
            if (nameB < 0)
                return null;
            authorIdent = parsePersonIdent(raw, nameB, Charset.defaultCharset());
        }

        return authorIdent;
    }

    // Imported from
    // https://github.com/eclipse/jgit/blob/v3.0.0.201305080800-m7/org.eclipse.jgit/src/org/eclipse/jgit/revwalk/RevCommit.java
    // and modified by Yi EungJun
    /**
     * Parse the committer identity from the raw buffer.
     * <p>
     * This method parses and returns the content of the committer line, after
     * taking the commit's character set into account and decoding the committer
     * name and email address. This method is fairly expensive and produces a
     * new PersonIdent instance on each invocation. Callers should invoke this
     * method only if they are certain they will be outputting the result, and
     * should cache the return value for as long as necessary to use all
     * information from it.
     * <p>
     * RevFilter implementations should try to use {@link RawParseUtils} to scan
     * the {@link RevCommit#getRawBuffer()} instead, as this will allow faster evaluation
     * of commits.
     *
     * @return identity of the committer (name, email) and the time the commit
     *         was made by the committer; null if no committer line was found.
     */
    public final PersonIdent getCommitterIdent() {
        if (committerIdent == null) {
            final byte[] raw = revCommit.getRawBuffer();
            final int nameB = RawParseUtils.committer(raw, 0);
            if (nameB < 0)
                return null;
            committerIdent = parsePersonIdent(raw, nameB, Charset.defaultCharset());
        }

        return committerIdent;
    }

    public static Charset parseEncoding(final byte[] b, Charset fallback) {
        try {
            return RawParseUtils.parseEncoding(b);
        } catch (UnsupportedCharsetException badName) {
            return fallback;
        }
    }

    // Imported from
    // https://github.com/eclipse/jgit/blob/v3.0.0.201305080800-m7/org.eclipse.jgit/src/org/eclipse/jgit/util/RawParseUtils.java
    // and modified by Yi EungJun
    /**
     * Parse a name line (e.g. author, committer, tagger) into a PersonIdent.
     * <p>
     * When passing in a value for <code>nameB</code> callers should use the
     * return value of {@link RawParseUtils#author(byte[], int)} or
     * {@link RawParseUtils#committer(byte[], int)}, as these methods provide the proper
     * position within the buffer.
     *
     * @param raw
     *            the buffer to parse character data from.
     * @param nameB
     *            first position of the identity information. This should be the
     *            first position after the space which delimits the header field
     *            name (e.g. "author" or "committer") from the rest of the
     *            identity line.
     * @return the parsed identity or null in case the identity could not be
     *         parsed.
     */
    public static PersonIdent parsePersonIdent(final byte[] raw, final int nameB,
                                               Charset fallback) {
        // This line and the third parameter of this method is added by Yi EungJun. 2013/12/10
        final Charset cs = (fallback == null) ?
                RawParseUtils.parseEncoding(raw) : parseEncoding(raw, fallback);

        final int emailB = RawParseUtils.nextLF(raw, nameB, '<');
        final int emailE = RawParseUtils.nextLF(raw, emailB, '>');
        if (emailB >= raw.length || raw[emailB] == '\n' ||
                (emailE >= raw.length - 1 && raw[emailE - 1] != '>'))
            return null;

        final int nameEnd = emailB - 2 >= nameB && raw[emailB - 2] == ' ' ?
                emailB - 2 : emailB - 1;
        final String name = RawParseUtils.decode(cs, raw, nameB, nameEnd);
        final String email = RawParseUtils.decode(cs, raw, emailB, emailE - 1);

        // Start searching from end of line, as after first name-email pair,
        // another name-email pair may occur. We will ignore all kinds of
        // "junk" following the first email.
        //
        // We've to use (emailE - 1) for the case that raw[email] is LF,
        // otherwise we would run too far. "-2" is necessary to position
        // before the LF in case of LF termination resp. the penultimate
        // character if there is no trailing LF.
        final int tzBegin = lastIndexOfTrim(raw, ' ',
                RawParseUtils.nextLF(raw, emailE - 1) - 2) + 1;
        if (tzBegin <= emailE) // No time/zone, still valid
            return new PersonIdent(name, email, 0, 0);

        final int whenBegin = Math.max(emailE,
                lastIndexOfTrim(raw, ' ', tzBegin - 1) + 1);
        if (whenBegin >= tzBegin - 1) // No time/zone, still valid
            return new PersonIdent(name, email, 0, 0);

        final long when = RawParseUtils.parseLongBase10(raw, whenBegin, null);
        final int tz = RawParseUtils.parseTimeZoneOffset(raw, tzBegin);
        return new PersonIdent(name, email, when * 1000L, tz);
    }

    // Imported from
    // https://github.com/eclipse/jgit/blob/v3.0.0.201305080800-m7/org.eclipse.jgit/src/org/eclipse/jgit/util/RawParseUtils.java
    static boolean hasLF(final byte[] r, int b, final int e) {
        while (b < e)
            if (r[b++] == '\n')
                return true;
        return false;
    }

    // Imported from
    // https://github.com/eclipse/jgit/blob/v3.0.0.201305080800-m7/org.eclipse.jgit/src/org/eclipse/jgit/util/RawParseUtils.java
    private static int lastIndexOfTrim(byte[] raw, char ch, int pos) {
        while (pos >= 0 && raw[pos] == ' ')
            pos--;

        while (pos >= 0 && raw[pos] != ch)
            pos--;

        return pos;
    }
}
