/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
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

import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.junit.Test;
import playRepository.DiffLine;
import playRepository.DiffLineType;
import playRepository.FileDiff;
import playRepository.Hunk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class FileDiffTest {
    @Test
    public void getHunks() throws IOException {
        // given
        FileDiff fileDiff = new FileDiff();
        fileDiff.a = new RawText("apple\nbanana\ncat\n".getBytes());
        fileDiff.b = new RawText("apple\nbanana\ncorn\n".getBytes());
        DiffAlgorithm diffAlgorithm =
                DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM);
        fileDiff.editList = diffAlgorithm.diff(RawTextComparator.DEFAULT, fileDiff.a,
                        fileDiff.b);

        // when
        List<Hunk> hunks = fileDiff.getHunks();

        // then
        Hunk expectedHunk = new Hunk();
        expectedHunk.beginA = 0;
        expectedHunk.endA = 3;
        expectedHunk.beginB = 0;
        expectedHunk.endB = 3;
        expectedHunk.lines = new ArrayList<>();
        expectedHunk.lines.add(new DiffLine(fileDiff, DiffLineType.CONTEXT, 0, 0, "apple"));
        expectedHunk.lines.add(new DiffLine(fileDiff, DiffLineType.CONTEXT, 1, 1, "banana"));
        expectedHunk.lines.add(new DiffLine(fileDiff, DiffLineType.REMOVE, 2, null, "cat"));
        expectedHunk.lines.add(new DiffLine(fileDiff, DiffLineType.ADD, null, 2, "corn"));
        ArrayList<Hunk> expectedHunks = new ArrayList<>();
        expectedHunks.add(expectedHunk);
        assertThat(hunks).describedAs("Test FileDiff.hunks").isEqualTo(expectedHunks);
    }

}
