package playRepository;

public class DiffLine {
    public final DiffLineType kind;
    public final Integer numA;
    public final Integer numB;
    public final String content;
    public FileDiff file;

    public DiffLine(FileDiff file, DiffLineType type, Integer lineNumA, Integer lineNumB,
                    String content) {
        this.file = file;
        this.kind = type;
        this.numA = lineNumA;
        this.numB = lineNumB;
        this.content = content;
    }
}