/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author kjkmadness
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

import static org.fest.assertions.Assertions.*;

import org.eclipse.jgit.lib.FileMode;
import org.junit.*;

public class FileDiffTest {
    private FileDiff diff;

    @Before
    public void before() {
        diff = new FileDiff();
    }

    @Test
    public void fileCreated() {
        // Given
        initFileMode(FileMode.MISSING, FileMode.REGULAR_FILE);

        // When
        // Then
        assertThat(diff.isFileModeChanged()).isFalse();
    }

    @Test
    public void fileDeleted() {
        // Given
        initFileMode(FileMode.REGULAR_FILE, FileMode.MISSING);

        // When
        // Then
        assertThat(diff.isFileModeChanged()).isFalse();
    }

    @Test
    public void fileModeNotChanged() {
        // Given
        initFileMode(FileMode.REGULAR_FILE, FileMode.REGULAR_FILE);

        // When
        // Then
        assertThat(diff.isFileModeChanged()).isFalse();
    }

    @Test
    public void fileModeChanged() {
        // Given
        initFileMode(FileMode.EXECUTABLE_FILE, FileMode.REGULAR_FILE);

        // When
        // Then
        assertThat(diff.isFileModeChanged()).isTrue();
    }

    /*
     * FileDiff.fileModeChanged 테스트를 위한 초기화
     */
    private void initFileMode(FileMode oldMode, FileMode newMode) {
        diff.oldMode = oldMode;
        diff.newMode = newMode;
    }
}
