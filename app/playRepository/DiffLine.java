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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DiffLine diffLine = (DiffLine) o;

        if (content != null ? !content.equals(diffLine.content) : diffLine.content != null)
            return false;
        if (file != null ? !file.equals(diffLine.file) : diffLine.file != null) return false;
        if (kind != diffLine.kind) return false;
        if (numA != null ? !numA.equals(diffLine.numA) : diffLine.numA != null) return false;
        if (numB != null ? !numB.equals(diffLine.numB) : diffLine.numB != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = kind != null ? kind.hashCode() : 0;
        result = 31 * result + (numA != null ? numA.hashCode() : 0);
        result = 31 * result + (numB != null ? numB.hashCode() : 0);
        result = 31 * result + (content != null ? content.hashCode() : 0);
        result = 31 * result + (file != null ? file.hashCode() : 0);
        return result;
    }
}
