package com.jobsapply.platform.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Reads a bounded text request body (uploads), rejecting oversized input before buffering it all. */
public final class RequestBodies {

    public static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

    private RequestBodies() {}

    public static String readText(HttpServletRequest request) throws IOException {
        return new String(readBytes(request), StandardCharsets.UTF_8);
    }

    /** The raw body, unmodified (binary uploads such as PDFs). */
    public static byte[] readBytes(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_UPLOAD_BYTES) {
            throw tooLarge();
        }
        try (InputStream in = request.getInputStream()) {
            byte[] bytes = in.readNBytes(MAX_UPLOAD_BYTES + 1);
            if (bytes.length > MAX_UPLOAD_BYTES) {
                throw tooLarge();
            }
            return bytes;
        }
    }

    private static ResponseStatusException tooLarge() {
        return new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Upload exceeds " + MAX_UPLOAD_BYTES + " bytes");
    }
}
