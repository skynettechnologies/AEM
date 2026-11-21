package com.skynettechnologies.aem.aioa.core.filters;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.UnaryOperator;

import javax.servlet.ServletOutputStream;

import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.wrappers.SlingHttpServletResponseWrapper;

/**
 * Keeps what the page writes through getWriter() in memory, so the filter can change it before it is sent.
 * Output through getOutputStream() (binary answers) is passed through untouched. The content length is never
 * forwarded while buffering, because the body may grow.
 */
final class BufferedHtmlResponse extends SlingHttpServletResponseWrapper {

    private final CharArrayWriter buffer = new CharArrayWriter(32 * 1024);
    private PrintWriter writer;
    private boolean streamUsed;

    BufferedHtmlResponse(SlingHttpServletResponse response) {
        super(response);
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (streamUsed) {
            return super.getWriter();
        }
        if (writer == null) {
            writer = new PrintWriter(buffer);
        }
        return writer;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter() has already been called");
        }
        streamUsed = true;
        return super.getOutputStream();
    }

    @Override
    public void setContentLength(int len) {
        if (streamUsed) {
            super.setContentLength(len);
        }
    }

    @Override
    public void setContentLengthLong(long len) {
        if (streamUsed) {
            super.setContentLengthLong(len);
        }
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush(); // stays in memory until writeTo()
        } else {
            super.flushBuffer();
        }
    }

    @Override
    public void resetBuffer() {
        super.resetBuffer();
        buffer.reset();
    }

    @Override
    public void reset() {
        super.reset();
        buffer.reset();
    }

    /**
     * Sends the buffered page to the real response, changed by transform when it returns a value
     * (null keeps the page as it was). Does nothing when the page used the output stream or wrote nothing.
     */
    void writeTo(SlingHttpServletResponse response, UnaryOperator<String> transform) throws IOException {
        if (writer == null) {
            return;
        }
        writer.flush();
        String body = buffer.toString();
        String changed = body.isEmpty() ? null : transform.apply(body);
        response.getWriter().write(changed != null ? changed : body);
    }
}
