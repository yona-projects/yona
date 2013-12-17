package models;

import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.validation.constraints.NotNull;

/**
 * @author Keesun Baik
 */
@Embeddable
public class CodeRange {

    enum Side {
        A, B;
    }

    public String path;

    @Enumerated(EnumType.STRING)
    public Side startSide;

    @NotNull
    public Integer startLine;

    @NotNull
    public Integer startColumn;

    @Enumerated(EnumType.STRING)
    public Side endSide;

    @NotNull
    public Integer endLine;

    @NotNull
    public Integer endColumn;

}
