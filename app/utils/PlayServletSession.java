/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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

import java.util.Enumeration;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

public class PlayServletSession implements HttpSession {

    private ServletContext context;

    public PlayServletSession(ServletContext context) {
        this.context = context;
    }

    @Override
    public Object getAttribute(String arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getCreationTime() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLastAccessedTime() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxInactiveInterval() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServletContext getServletContext() {
        return this.context;
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public javax.servlet.http.HttpSessionContext getSessionContext() {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public Object getValue(String arg0) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public String[] getValueNames() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void invalidate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isNew() {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public void putValue(String arg0, Object arg1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeAttribute(String arg0) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public void removeValue(String arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttribute(String arg0, Object arg1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMaxInactiveInterval(int arg0) {
        throw new UnsupportedOperationException();
    }

}
