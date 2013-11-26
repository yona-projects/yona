/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
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
package utils;

import javax.script.Invocable;

/**
 * Copied from https://gist.github.com/UnquietCode/5614860
 *
 * Simple utility class for executing an object's method with any arguments
 *
 * @author Keesun Baik
 * @see http://momentjs.com/docs/
 * @see https://gist.github.com/UnquietCode/5614860
 */
public class JSInvocable {

    private final Invocable invocable;
    private final Object object;

    public JSInvocable(Invocable invocable, Object object) {
        this.invocable = invocable;
        this.object = object;
    }

    public String invoke(String method, Object... args) {
        if(args == null) {
            args = new Object[0];
        }

        try {
            return invocable.invokeMethod(object, method, args).toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
