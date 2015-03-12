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
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * loads moment javascript and returns moment object
 *
 * @author Keesun Baik
 * @see http://momentjs.com/docs/
 * @see https://gist.github.com/UnquietCode/5614860
 * @see JSInvocable
 */
public class MomentUtil {
    private static ScriptEngine engine = buildEngine();

    private static final String MOMENT_JS_FILE = "public/javascripts/lib/moment-with-langs.min.js";

    private static ScriptEngine buildEngine() {
        ScriptEngineManager manager = new ScriptEngineManager();
        InputStream is = null;
        Reader reader = null;
        ScriptEngine _engine = manager.getEngineByName("JavaScript");

        try {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(MOMENT_JS_FILE);
            reader = new InputStreamReader(is, Config.getCharset());
            _engine.eval(reader);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            if(reader != null) {
                try{ reader.close(); } catch (Exception e) { throw new RuntimeException(e); }
            }
            if(is != null) {
                try{ is.close(); } catch (Exception e) { throw new RuntimeException(e); }
            }
        }

        return _engine;
    }

    public static JSInvocable newMoment(Long epoch) {
        return newMoment(epoch, Constants.DEFAULT_LANGUAGE);
    }

    public static JSInvocable newMoment(Long epoch, String language) {
        Object moment;

        try {
            if (epoch == null) {
                moment = ((Invocable) engine).invokeFunction("moment");
            } else {
                moment = ((Invocable) engine).invokeFunction("moment", epoch);
            }
            ((Invocable) engine).invokeMethod(moment, "lang", language);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        return new JSInvocable((Invocable) engine, moment);
    }
}
