/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Daegeun Kim
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

public class GitRef extends VCSRef {
    public GitRef(String name) {
        super(name);
    }

    @Override
    public String name() {
        if (name == null) {
            return null;
        }
        if (name.startsWith("refs") && name.split("/").length >= 3) {
            return name.split("/", 3)[2];
        }
        return name;
    }
}
