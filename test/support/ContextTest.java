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
package support;

import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.*;

import play.api.mvc.RequestHeader;
import play.mvc.Http.*;

public abstract class ContextTest {
    private FakeContext context;

    @Before
    public final void initContext() {
        context = new FakeContext(1L,
                mock(RequestHeader.class),
                mock(Request.class),
                Collections.<String, String> emptyMap(),
                Collections.<String, String> emptyMap(),
                Collections.<String, Object> emptyMap());
        Context.current.set(context);
    }

    @After
    public final void removeContext() {
        Context.current.remove();
    }

    public FakeContext context() {
        return context;
    }

    protected static class FakeContext extends Context {
        private Response response;
        private Map<String, String[]> headers = new HashMap<>();

        FakeContext(Long id, RequestHeader header, Request request,
                Map<String, String> sessionData, Map<String, String> flashData,
                Map<String, Object> args) {
            super(id, header, request, sessionData, flashData, args);
            response = mock(Response.class);
            when(request.cookies()).thenReturn(mock(Cookies.class));
            when(request.headers()).thenReturn(headers);
        }

        @Override
        public Response response() {
            return response;
        }

        public FakeContext withHeader(String name, String value) {
            headers.put(name, new String[] {value});
            when(request().getHeader(name)).thenReturn(value);
            return this;
        }

        public FakeContext withCookie(String name, String value) {
            Cookie cookie = mock(Cookie.class);
            when(cookie.name()).thenReturn(name);
            when(cookie.value()).thenReturn(value);
            when(request().cookie(name)).thenReturn(cookie);
            when(request().cookies().get(name)).thenReturn(cookie);
            return this;
        }

        public FakeContext withSession(String name, String value) {
            session().put(name, value);
            return this;
        }

        public FakeContext withArg(String name, Object value) {
            args.put(name, value);
            return this;
        }
    }
}
