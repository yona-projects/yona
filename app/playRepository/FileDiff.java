package playRepository;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Many lines of code are imported from
 * https://github.com/eclipse/jgit/blob/v2.3.1.201302201838-r/org.eclipse.jgit/src/org/eclipse/jgit/diff/DiffFormatter.java
 */
public class FileDiff {
    public RawText a;
    public RawText b;
    public EditList editList;
    public String commitA;
    public String commitB;
    public String pathA;
    public String pathB;
    public int context = 3;
    private boolean isEndOfLineMissing;
    public DiffEntry.ChangeType changeType;

    /**
     * Get list of hunks
     *
	 * @throws java.io.IOException
	 */
	public List<Hunk> getHunks()
			throws IOException {

        List<Hunk> hunks = new ArrayList<>();

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
					isEndOfLineMissing = checkEndOfLineMissing(a, aCur);
					aCur++;
					bCur++;
				} else if (aCur < curEdit.getEndA()) {
                    hunk.lines.add(new DiffLine(this, DiffLineType.REMOVE, aCur, bCur,
                            a.getString(aCur)));
                    isEndOfLineMissing = checkEndOfLineMissing(a, aCur);
					aCur++;
				} else if (bCur < curEdit.getEndB()) {
                    hunk.lines.add(new DiffLine(this, DiffLineType.ADD, aCur, bCur,
                            b.getString(bCur)));
                    isEndOfLineMissing = checkEndOfLineMissing(a, aCur);
					bCur++;
				}

				if (end(curEdit, aCur, bCur) && ++curIdx < editList.size())
					curEdit = editList.get(curIdx);
			}

            hunks.add(hunk);
		}

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
}
