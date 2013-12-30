package models;

import com.avaje.ebean.annotation.EnumValue;
import play.data.validation.Constraints;

import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;

/**
 * @author Keesun Baik
 */
@Embeddable
public class CodeRange {

    public enum Side {
        @EnumValue("A") A,
        @EnumValue("B") B
    }

    public String path;

    @Enumerated(EnumType.STRING)
    public Side startSide;

    @Constraints.Required
    public Integer startLine;

    @Constraints.Required
    public Integer startColumn;

    @Enumerated(EnumType.STRING)
    public Side endSide;

    @Constraints.Required
    public Integer endLine;

    @Constraints.Required
    public Integer endColumn;

}
