/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik
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

import org.eclipse.jgit.transport.ReceiveCommand;

import java.util.Collection;

/**
 * {@link actors.PostReceiveActor}에 필요한 정보를 전달할 때 사용한다.
 *
 * @author Keesun Baik
 */
public class PostReceiveMessage {

    /**
     * 성공한 커맨드 목록.
     */
    private Collection<ReceiveCommand> commands;

    /**
     * Receive 관련 프로젝트.
     */
    private Project project;

    /**
     * ReceivePack을 보낸 사용자
     */
    private User user;

    public PostReceiveMessage(Collection<ReceiveCommand> commands, Project project, User user) {
        this.commands = commands;
        this.project = project;
        this.user = user;
    }

    public Collection<ReceiveCommand> getCommands() {
        return commands;
    }

    public Project getProject() {
        return project;
    }

    public User getUser() {
        return user;
    }
}
