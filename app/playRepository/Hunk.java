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
    }

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
