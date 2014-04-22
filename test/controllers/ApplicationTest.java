/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Park Jongbhin
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
package controllers;

import org.junit.Test;
import play.mvc.Result;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.*;

public class ApplicationTest {

    @Test
    public void callIndex() {
	running(support.Helpers.makeTestApplication(), new Runnable() {
    		@Override
	        public void run() {
		        Result result = callAction(controllers.routes.ref.Application.index());
		        assertThat(status(result)).isEqualTo(OK);
		        assertThat(contentType(result)).isEqualTo("text/html");
		        assertThat(charset(result)).isEqualTo("utf-8");
                assertThat(status(result)).isEqualTo(OK);
    		}
    	});
    }
}
