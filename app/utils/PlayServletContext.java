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

import javax.servlet.*;
import javax.servlet.FilterRegistration.*;
import javax.servlet.descriptor.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class PlayServletContext implements ServletContext {

    @Override
    public Dynamic addFilter(String arg0, String arg1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Dynamic addFilter(String arg0, Filter arg1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Dynamic addFilter(String arg0, Class<? extends Filter> arg1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addListener(String arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends EventListener> void addListener(T arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addListener(Class<? extends EventListener> arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public javax.servlet.ServletRegistration.Dynamic addServlet(String arg0, String arg1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public javax.servlet.ServletRegistration.Dynamic addServlet(String arg0, Servlet arg1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public javax.servlet.ServletRegistration.Dynamic addServlet(String arg0,
            Class<? extends Servlet> arg1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> arg0) throws ServletException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends EventListener> T createListener(Class<T> arg0) throws ServletException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> arg0) throws ServletException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void declareRoles(String... arg0) {
        throw new UnsupportedOperationException();
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
    public ClassLoader getClassLoader() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServletContext getContext(String arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getContextPath() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getEffectiveMajorVersion() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getEffectiveMinorVersion() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FilterRegistration getFilterRegistration(String arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getInitParameter(String arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        throw new UnsupportedOperationException();
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMajorVersion() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getMimeType(String arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMinorVersion() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String arg0) {
        throw new UnsupportedOperationException();

    }

    @Override
    public String getRealPath(String arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public URL getResource(String arg0) throws MalformedURLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getResourceAsStream(String arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getResourcePaths(String arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getServerInfo() {
        return play.Configuration.root().getString("application.server");
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public Servlet getServlet(String arg0) throws ServletException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getServletContextName() {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public Enumeration<String> getServletNames() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServletRegistration getServletRegistration(String arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public Enumeration<Servlet> getServlets() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void log(String arg0) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public void log(Exception arg0, String arg1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void log(String arg0, Throwable arg1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeAttribute(String arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttribute(String arg0, Object arg1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean setInitParameter(String arg0, String arg1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> arg0) {
        throw new UnsupportedOperationException();
    }

    public String getVirtualServerName() {
        throw new UnsupportedOperationException();
    }
}
