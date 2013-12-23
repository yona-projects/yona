package playRepository;

import org.eclipse.jgit.diff.EditList;

import java.util.ArrayList;
import java.util.List;

public class Hunk {
    public int beginA;
    public int endA;
    public int beginB;
    public int endB;
    public List<DiffLine> lines = new ArrayList<>();

    public int size() {
        int length = 0;
        for (DiffLine line : lines) {
            length += line.content.length();
        }
        return length;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Hunk hunk = (Hunk) o;

        if (beginA != hunk.beginA) return false;
        if (beginB != hunk.beginB) return false;
        if (endA != hunk.endA) return false;
        if (endB != hunk.endB) return false;
        if (lines != null ? !lines.equals(hunk.lines) : hunk.lines != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = beginA;
        result = 31 * result + endA;
        result = 31 * result + beginB;
        result = 31 * result + endB;
        result = 31 * result + (lines != null ? lines.hashCode() : 0);
        return result;
    }
}
