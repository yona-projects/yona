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
    @Override
    public javax.servlet.http.HttpSessionContext getSessionContext() {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     */
    @Override
    public Object getValue(String arg0) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     */
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
