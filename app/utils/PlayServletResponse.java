/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  getCharsetFromContentType()
 */

package utils;

import play.mvc.Http;
import play.mvc.Http.Response;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

public class PlayServletResponse implements HttpServletResponse {

    private final PipedInputStream inputStream;
    private Response response;
    private String characterEncoding;
    private int status = 0;
    private PrintWriter pw;
    private ChunkedOutputStream outputStream;
    private final Object statusLock;
    private boolean committed;

    /**
     * Waits until the HTTP status code of this response is given, then returns
     * the code.
     *
     * @return the HTTP status code
     * @throws InterruptedException
     */
    public int waitAndGetStatus() throws InterruptedException {
        Object statusLock = getStatusLock();
        synchronized (statusLock) {
            statusLock.wait();
            return getStatus();
        }
    }

    public Object getStatusLock() {
        return statusLock;
    }

    class ChunkedOutputStream extends ServletOutputStream {
        private byte[] buffer = new byte[1024 * 1024];
        private int offset = 0;
        private OutputStream target;

        public ChunkedOutputStream(OutputStream target) {
            this.target = target;
        }

        @Override
        public void write(int b) throws IOException {
            if (offset >= buffer.length - 1) {
                flush();
            }
            buffer[offset++] = (byte) b;
        }

        @Override
        public void write(byte[] b) throws IOException {
            synchronized (statusLock) {
                // Make sure HTTP status and header is specified.
                statusLock.notifyAll();
                committed = true;
            }
            target.write(b);
        }

        @Override
        public void flush() throws IOException {
            synchronized (statusLock) {
                // Make sure HTTP status and header is specified.
                statusLock.notifyAll();
            }
            byte[] b = Arrays.copyOf(buffer, offset);
            target.write(b);
            offset = 0;
        }

        @Override
        public void close() throws IOException {
            offset = 0;
            target.close();
            super.close();
        }

        public void setWriteListener(WriteListener writeListener) {
            throw new UnsupportedOperationException();
        }

        public boolean isReady() {
            throw new UnsupportedOperationException();
        }
    }

    public PlayServletResponse(Response response) throws IOException {
        this.response = response;
        this.statusLock = new Object();
        this.inputStream = new PipedInputStream();
        this.outputStream = new ChunkedOutputStream(new PipedOutputStream(this.inputStream));
        this.pw = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(this.outputStream, Config.getCharset())),
                false);
    }

    @Override
    public void flushBuffer() throws IOException {
        getWriter().flush();
    }

    @Override
    public int getBufferSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCharacterEncoding() {
        if (characterEncoding != null) {
            return characterEncoding;
        }

        return getCharsetFromContentType(getContentType());
    }


    @Override
    public String getContentType() {
        return getHeader(Http.HeaderNames.CONTENT_TYPE);
    }

    @Override
    public Locale getLocale() {
        throw new UnsupportedOperationException();
    }

    public PipedInputStream getInputStream() {
        return this.inputStream;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return pw;
    }

    @Override
    public boolean isCommitted() {
        return committed;
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resetBuffer() {
        try {
            getWriter().flush();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        // FIXME: pipedOutput does not have reset method. Is it required?
        // pipedOutput.reset();
    }

    @Override
    public void setBufferSize(int arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCharacterEncoding(String characterEncoding) {
        this.characterEncoding = characterEncoding;
    }

    @Override
    public void setContentLength(int length) {
        this.response.setHeader(Http.HeaderNames.CONTENT_LENGTH, Integer.toString(length));
    }

    public void setContentLengthLong(long length) {
        this.response.setHeader(Http.HeaderNames.CONTENT_LENGTH, Long.toString(length));
    }

    @Override
    public void setContentType(String type) {
        this.response.setContentType(type);

    }

    @Override
    public void setLocale(Locale locale) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addCookie(Cookie cookie) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addDateHeader(String name, long date) {
        addHeader(name, FastHttpDateFormat.formatDate(date, null));
    }

    @Override
    public void addHeader(String name, String value) {
        String head = this.response.getHeaders().get(name);
        String newValue;
        if(head == null || head.trim().isEmpty()) {
            newValue = value;
        } else {
            newValue = head + "," + value;
        }
        this.response.setHeader(name, newValue);
    }

    @Override
    public void addIntHeader(String name, int value) {
           addHeader(name, value +"");
    }

    @Override
    public boolean containsHeader(String name) {
        return this.response.getHeaders().containsKey(name);
    }

    @Override
    public String encodeRedirectURL(String url) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public String encodeRedirectUrl(String url) {
        return encodeRedirectURL(url);
    }

    @Override
    public String encodeURL(String arg0) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public String encodeUrl(String arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getHeader(String headerName) {
        for(String h: response.getHeaders().keySet()) {
            if(headerName.toLowerCase().equals(h.toLowerCase())) {
                return  response.getHeaders().get(h);
            }
        }

        return null;
    }

    @Override
    public Collection<String> getHeaderNames() {
        return this.response.getHeaders().keySet();
    }

    @Override
    public Collection<String> getHeaders(String name) {
        return Arrays.asList(this.response.getHeaders().get(name).split(","));
    }

    @Override
    public int getStatus() {
        return this.status;
    }

    @Override
    public void sendError(int statusCode) throws IOException {
        // FIXME response should be returned at this time.
        setStatus(statusCode);
    }

    @Override
    public void sendError(int statusCode, String msg) throws IOException {
        // FIXME response should be returned at this time.
        setStatus(statusCode);
        resetBuffer();
        if (msg != null) {
            play.Logger.error(msg);
            getWriter().write(msg);
            response.setHeader(Http.HeaderNames.CONTENT_TYPE, "text/plain");
        } else {
            response.getHeaders().remove(Http.HeaderNames.CONTENT_TYPE);
        }
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        // FIXME response should be returned at this time.
        response.setHeader(Http.HeaderNames.LOCATION, location);
        setStatus(Http.Status.FOUND);
    }

    @Override
    public void setDateHeader(String name, long date) {
        this.response.setHeader(name, FastHttpDateFormat.formatDate(date, null));
    }

    @Override
    public void setHeader(String name, String value) {
        if (name == null || name.length() == 0 || value == null) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        response.setHeader(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        response.setHeader(name, Integer.toString(value));
    }

    @Override
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public void setStatus(int status, String msg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Parse the character encoding from the specified content type header.
     * If the content type is null, or there is no explicit character encoding,
     * <code>null</code> is returned.
     *
     * @param contentType a content type header
     */
    private static String getCharsetFromContentType(String contentType) {

        if (contentType == null) {
            return (null);
        }
        int start = contentType.indexOf("charset=");
        if (start < 0) {
            return (null);
        }
        String encoding = contentType.substring(start + 8);
        int end = encoding.indexOf(';');
        if (end >= 0) {
            encoding = encoding.substring(0, end);
        }
        encoding = encoding.trim();
        if ((encoding.length() > 2) && (encoding.startsWith("\""))
            && (encoding.endsWith("\""))) {
            encoding = encoding.substring(1, encoding.length() - 1);
        }
        return (encoding.trim());
    }

    public String getVirtualServerName() {
        throw new UnsupportedOperationException();
    }
}
