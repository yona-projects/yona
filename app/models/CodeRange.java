/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
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
package models;

import com.avaje.ebean.annotation.EnumValue;
import play.data.validation.Constraints;
import playRepository.DiffLine;
import playRepository.FileDiff;

import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

/**
 * @author Keesun Baik
 */
@Embeddable
public class CodeRange {

    public boolean isFor(FileDiff diff) {
        if (endSide.equals(Side.B)
                && !diff.pathB.equals(path)) {
            return false;
        }

        if (endSide.equals(Side.A)
                && !diff.pathA.equals(path)) {
            return false;
        }
        return true;
    }

    public boolean endsWith(DiffLine line) {
        return (endSide.equals(Side.A) && endLine.equals(line.numA)) ||
               (endSide.equals(Side.B) && endLine.equals(line.numB));
    }

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
