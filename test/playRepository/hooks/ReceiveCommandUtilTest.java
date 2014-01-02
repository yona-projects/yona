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
package playRepository.hooks;

import static org.fest.assertions.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Type;
import org.junit.Test;

public class ReceiveCommandUtilTest {
    @Test
    public void isTypeMatching() {
        // Given
        ReceiveCommand delete = createMockReceiveCommand("delete", Type.DELETE);
        ReceiveCommand update = createMockReceiveCommand("update", Type.UPDATE);
        ReceiveCommand create = createMockReceiveCommand("create", Type.CREATE);

        // When
        // Then
        assertThat(ReceiveCommandUtil.isTypeMatching(delete, Type.DELETE)).isTrue();
        assertThat(ReceiveCommandUtil.isTypeMatching(update, Type.UPDATE)).isTrue();
        assertThat(ReceiveCommandUtil.isTypeMatching(create, Type.CREATE)).isTrue();
    }

    @Test
    public void getBranchesByCommandType() {
        // Given
        String deletedBranch = "delete";
        String updatedBranch = "update";
        String updatedNonFFBranch = "update_nonff";
        String createdBranch = "create";
        List<ReceiveCommand> commands = new ArrayList<>();
        commands.add(createMockReceiveCommand(deletedBranch, Type.DELETE));
        commands.add(createMockReceiveCommand(updatedBranch, Type.UPDATE));
        commands.add(createMockReceiveCommand(updatedNonFFBranch, Type.UPDATE_NONFASTFORWARD));
        commands.add(createMockReceiveCommand(createdBranch, Type.CREATE));

        // When
        Set<String> delete = ReceiveCommandUtil.getRefNamesByCommandType(commands,
                Type.DELETE);
        Set<String> update = ReceiveCommandUtil.getRefNamesByCommandType(commands,
                Type.UPDATE, Type.UPDATE_NONFASTFORWARD);
        Set<String> create = ReceiveCommandUtil.getRefNamesByCommandType(commands,
                Type.CREATE);

        // Then
        assertThat(delete).containsOnly(deletedBranch);
        assertThat(update).containsOnly(updatedBranch, updatedNonFFBranch);
        assertThat(create).containsOnly(createdBranch);
    }

    private ReceiveCommand createMockReceiveCommand(String refName, Type type) {
        ReceiveCommand command = mock(ReceiveCommand.class);
        when(command.getRefName()).thenReturn(refName);
        when(command.getType()).thenReturn(type);
        return command;
    }
}
