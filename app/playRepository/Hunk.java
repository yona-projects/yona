package playRepository;

import org.eclipse.jgit.diff.EditList;

import java.util.ArrayList;
import java.util.List;

public class Hunk extends EditList {

    private static final long serialVersionUID = 1L;
    public int beginA;
    public int endA;
    public int beginB;
    public int endB;
    public List<DiffLine> lines = new ArrayList<>();
    public boolean isEndOfLineMissing;
}
