/**
 *  Yona, 21st Century Project Hosting SW
 *  <p>
 *  Copyright Yona & Yobi Authors & NAVER Corp.
 *  https://yona.io
 **/

package utils;

import play.mvc.Results.Chunks;

import java.io.IOException;
import java.io.OutputStream;

//
// ChunkedOutputStream is made by referring to BufferedOutputStream.java
//
public class ChunkedOutputStream extends OutputStream {

    Chunks.Out<byte[]> out;
    /**
     * The internal buffer where data is stored.
     */
    protected byte buf[];

    /**
     * The number of valid bytes in the buffer. This value is always
     * in the range <tt>0</tt> through <tt>buf.length</tt>; elements
     * <tt>buf[0]</tt> through <tt>buf[count-1]</tt> contain valid
     * byte data.
     */
    protected int count;

    public ChunkedOutputStream(Chunks.Out<byte[]> out, int size) {
        if (size <= 0) {
            buf = new byte[16384];
        } else {
            buf = new byte[size];
        }
        this.out = out;
    }

    /**
     * Writes the specified byte to this buffered output stream.
     *
     * @param      b   the byte to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public synchronized void write(int b) throws IOException {
        if (count >= buf.length) {
            flushBuffer();
        }
        buf[count++] = (byte)b;
    }

    public void write(byte b[]) throws IOException {
        throw new UnsupportedOperationException("write(byte b[])");
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array
     * starting at offset <code>off</code> to this buffered output stream.
     *
     * <p> Ordinarily this method stores bytes from the given array into this
     * stream's buffer, flushing the buffer to the underlying output stream as
     * needed.  If the requested length is at least as large as this stream's
     * buffer, however, then this method will flush the buffer and write the
     * bytes directly to the underlying output stream.  Thus redundant
     * <code>BufferedOutputStream</code>s will not copy data unnecessarily.
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public synchronized void write(byte b[], int off, int len) throws IOException {
        if (len >= buf.length) {
            /* If the request length exceeds the size of the output buffer,
               flush the output buffer and then write the data directly.
               In this way buffered streams will cascade harmlessly. */
            flushBuffer();
            write(b, off, len);
            return;
        }
        if (len > buf.length - count) {
            flushBuffer();
        }
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    private void flushBuffer() throws IOException {
        if (count > 0) {
            chunkOut();
        }
    }

    @Override
    public void close() throws IOException {
        if (count > 0) {
            chunkOut();
        }
        out.close();
    }

    private void chunkOut() {
        byte remainBuf[] = new byte[count];
        System.arraycopy(buf, 0, remainBuf,0, count);
        out.write(remainBuf);
        count = 0;
    }
}
