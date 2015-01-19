/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
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

import models.CodeRange;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.FileMode;

import java.util.*;


/**
 * Many lines of code are imported from
 * https://github.com/eclipse/jgit/blob/v2.3.1.201302201838-r/org.eclipse.jgit/src/org/eclipse/jgit/diff/DiffFormatter.java
 */
public class FileDiff {
    public static final int SIZE_LIMIT = 500 * 1024;
    public static final int LINE_LIMIT = 5000;
    private Set<Error> errors = new HashSet<>();

    public Integer getInterestLine() {
        return interestLine;
    }

    public void setInterestLine(Integer interestLine) {
        this.interestLine = interestLine;
        hunks = null;
    }

    public CodeRange.Side getInterestSide() {
        return interestSide;
    }

    public void setInterestSide(CodeRange.Side interestSide) {
        this.interestSide = interestSide;
        hunks = null;
    }

    public enum Error {A_SIZE_EXCEEDED, B_SIZE_EXCEEDED, DIFF_SIZE_EXCEEDED, OTHERS_SIZE_EXCEEDED }
    public RawText a;
    public RawText b;
    public EditList editList;
    public String commitA;
    public String commitB;
    public String pathA;
    public String pathB;
    public int context = 3;
    public boolean isBinaryA = false;
    public boolean isBinaryB = false;
    public DiffEntry.ChangeType changeType;
    private Integer interestLine = null;
    private CodeRange.Side interestSide = null;
    public FileMode oldMode;
    public FileMode newMode;
    private Hunks hunks;

    public static class Hunks extends ArrayList<Hunk> {
        private static final long serialVersionUID = -2359650678446017697L;
        public int size;
        public int lines;
    }

    public static class SizeExceededHunks extends Hunks {
        private static final long serialVersionUID = 3089104397758709369L;
    }

    public static boolean isRawTextSizeExceeds(RawText rawText) {
        return getRawTextSize(rawText) > SIZE_LIMIT || rawText.size() > LINE_LIMIT;
    }

    public static int getRawTextSize(RawText rawText) {
        int size = 0;
        for(int i = 0; i < rawText.size(); i++) {
            size += rawText.getString(i).length();
        }
        return size;
    }

    /**
     * Get list of hunks
     */
    public Hunks getHunks() {
        if (hunks != null) {
            return hunks;
        }

        if (editList == null) {
            return null;
        }

        int size = 0;
        int lines = 0;

        hunks = new Hunks();

        for (int curIdx = 0; curIdx < editList.size();) {
            Hunk hunk = new Hunk();
            Edit curEdit = editList.get(curIdx);
            final int endIdx = findCombinedEnd(editList, curIdx);
            final Edit endEdit = editList.get(endIdx);

            int aCur = Math.max(0, curEdit.getBeginA() - context);
            int bCur = Math.max(0, curEdit.getBeginB() - context);
            final int aEnd = Math.min(a.size(), endEdit.getEndA() + context);
            final int bEnd = Math.min(b.size(), endEdit.getEndB() + context);

            hunk.beginA = aCur;
            hunk.endA = aEnd;
            hunk.beginB = bCur;
            hunk.endB = bEnd;

            while (aCur < aEnd || bCur < bEnd) {
                if (aCur < curEdit.getBeginA() || endIdx + 1 < curIdx) {
                    hunk.lines.add(new DiffLine(this, DiffLineType.CONTEXT, aCur, bCur,
                            a.getString(aCur)));
                    aCur++;
                    bCur++;
                } else if (aCur < curEdit.getEndA()) {
                    hunk.lines.add(new DiffLine(this, DiffLineType.REMOVE, aCur, null,
                            a.getString(aCur)));
                    aCur++;
                } else if (bCur < curEdit.getEndB()) {
                    hunk.lines.add(new DiffLine(this, DiffLineType.ADD, null, bCur,
                            b.getString(bCur)));
                    bCur++;
                }

                if (end(curEdit, aCur, bCur) && ++curIdx < editList.size())
                    curEdit = editList.get(curIdx);
            }

            if (getInterestLine() != null && getInterestSide() != null) {

                boolean added = false;
                switch(getInterestSide()) {
                    case A:
                        if (hunk.beginA <= getInterestLine() && hunk.endA >= getInterestLine()) {
                            hunks.add(hunk);
                            size += hunk.size();
                            lines += hunk.lines.size();
                            added = true;
                        }
                        break;
                    case B:
                        if (hunk.beginB <= getInterestLine() && hunk.endB >= getInterestLine()) {
                            hunks.add(hunk);
                            size += hunk.size();
                            lines += hunk.lines.size();
                            added = true;
                        }
                        break;
                    default:
                        break;
                }
                if (added) {
                    break;
                }
            } else {
                hunks.add(hunk);
                size += hunk.size();
                lines += hunk.lines.size();
            }

            if (size > SIZE_LIMIT || lines > LINE_LIMIT) {
                hunks = new SizeExceededHunks();
                return hunks;
            }
        }

        hunks.size = size;
        hunks.lines = lines;

        return hunks;
    }

    private int findCombinedEnd(final List<Edit> edits, final int i) {
        int end = i + 1;
        while (end < edits.size()
                && (combineA(edits, end) || combineB(edits, end)))
            end++;
        return end - 1;
    }

    private boolean combineA(final List<Edit> e, final int i) {
        return e.get(i).getBeginA() - e.get(i - 1).getEndA() <= 2 * context;
    }

    private boolean combineB(final List<Edit> e, final int i) {
        return e.get(i).getBeginB() - e.get(i - 1).getEndB() <= 2 * context;
    }

    private static boolean end(final Edit edit, final int a, final int b) {
        return edit.getEndA() <= a && edit.getEndB() <= b;
    }

    private boolean checkEndOfLineMissing(final RawText text, final int line) {
        return line + 1 == text.size() && text.isMissingNewlineAtEnd();
    }

    /**
     * Remove every line not related to the given lines which "interest".
     *
     * Do nothing if {@link #editList} is null, because a file has been added
     * or deleted.
     *
     * @param lineA an added line which interests. Ignore if it is <code>null</code>
     * @param lineB an removed line which interests. Ignored if it is <code>null</code>
     */
    public void updateRange(Integer lineA, Integer lineB) {
        if (editList == null) {
            return;
        }

        EditList newEditList = new EditList();

        for (Edit edit: editList) {
            if (lineA != null) {
                if ((lineA >= edit.getBeginA() - context) && (lineA <= edit.getEndA() + context)) {
                    newEditList.add(edit);
                }
            }

            if (lineB != null) {
                if ((lineB >= edit.getBeginB() - context) && (lineB <= edit.getEndB() + context)) {
                    newEditList.add(edit);
                }
            }
        }

        editList = newEditList;
    }

    public boolean isFileModeChanged() {
        if (FileMode.MISSING.equals(oldMode.getBits())) {
            return false;
        }
        if (FileMode.MISSING.equals(newMode.getBits())) {
            return false;
        }
        return oldMode.getBits() != newMode.getBits();
    }

    public void addError(Error error) {
        this.errors.add(error);
    }

    public boolean hasAnyError(Error ... errors) {
        refreshErrors();

        for (Error error : errors) {
            if (this.errors.contains(error)) {
                return true;
            }
        }

        return false;
    }

    private void refreshErrors() {
        if (getHunks() instanceof SizeExceededHunks) {
            addError(Error.DIFF_SIZE_EXCEEDED);
        }

        // If editList is already produced, there is no need to concern about
        // the size of the raw text a or b
        if (editList == null && a != null && isRawTextSizeExceeds(a)) {
            addError(Error.A_SIZE_EXCEEDED);
        }
        if (editList == null && b != null && isRawTextSizeExceeds(b)) {
            addError(Error.B_SIZE_EXCEEDED);
        }
    }

    public boolean hasError(Error error) {
        refreshErrors();
        return this.errors.contains(error);
    }

    public boolean hasError() {
        refreshErrors();
        return !this.errors.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileDiff fileDiff = (FileDiff) o;

        if (commitA != null ? !commitA.equals(fileDiff.commitA) : fileDiff.commitA != null)
            return false;
        if (commitB != null ? !commitB.equals(fileDiff.commitB) : fileDiff.commitB != null)
            return false;
        if (editList != null ? !editList.equals(fileDiff.editList) : fileDiff.editList != null)
            return false;
        if (pathA != null ? !pathA.equals(fileDiff.pathA) : fileDiff.pathA != null) return false;
        if (pathB != null ? !pathB.equals(fileDiff.pathB) : fileDiff.pathB != null) return false;
        if (changeType != null ? !changeType.equals(fileDiff.changeType) : fileDiff.changeType != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = editList != null ? editList.hashCode() : 0;
        result = 31 * result + (commitA != null ? commitA.hashCode() : 0);
        result = 31 * result + (commitB != null ? commitB.hashCode() : 0);
        result = 31 * result + (pathA != null ? pathA.hashCode() : 0);
        result = 31 * result + (pathB != null ? pathB.hashCode() : 0);
        result = 31 * result + (changeType != null ? changeType.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FileDiff{" +
                "commitA='" + commitA + '\'' +
                ", commitB='" + commitB + '\'' +
                ", pathA='" + pathA + '\'' +
                ", pathB='" + pathB + '\'' +
                ", changeType=" + changeType +
                '}';
    }

}
