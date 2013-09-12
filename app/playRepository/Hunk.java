package playRepository;

import org.eclipse.jgit.diff.EditList;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: nori
 * Date: 13. 9. 11
 * Time: 오후 4:48
 * To change this template use File | Settings | File Templates.
 */
public class Hunk extends EditList {
    public int beginA;
    public int endA;
    public int beginB;
    public int endB;
    public List<DiffLine> lines = new ArrayList<>();
    public boolean isEndOfLineMissing;
}
