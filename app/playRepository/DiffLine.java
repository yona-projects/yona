package playRepository;

public class DiffLine {
    public final DiffLineType kind;
    public final Integer numA;
    public final Integer numB;
    public final String content;

    public DiffLine(DiffLineType type, Integer lineNumA, Integer lineNumB, String content) {
        this.kind = type;
        this.numA = lineNumA;
        this.numB = lineNumB;
        this.content = content;
    }
}
