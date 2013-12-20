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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Type;

public class ReceiveCommandUtil {
    /**
     * {@code commands} 중에서 {@code types} 에 지정된 type 을
     * 가지고 있는 것들의 refName set 을 얻는다
     * @param commands
     * @param types
     * @return
     */
    public static Set<String> getRefNamesByCommandType(Collection<ReceiveCommand> commands, Type... types) {
        Set<String> branches = new HashSet<>();
        for (ReceiveCommand command : commands) {
            if (isTypeMatching(command, types)) {
                branches.add(command.getRefName());
            }
        }
        return branches;
    }

    /**
     * {@code command} 의 type 이 {@code types} 중 하나인지 확인한다
     * @param command
     * @param types
     * @return
     */
    public static boolean isTypeMatching(ReceiveCommand command, Type... types) {
        return ArrayUtils.contains(types, command.getType());
    }
}
