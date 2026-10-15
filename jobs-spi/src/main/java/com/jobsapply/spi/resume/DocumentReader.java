package com.jobsapply.spi.resume;

import com.jobsapply.spi.common.Plugin;
import com.jobsapply.spi.common.PluginResult;
import java.util.Set;

/** Extracts the plain text of an uploaded document (PDF, DOCX, text). Runs locally. */
public interface DocumentReader extends Plugin {

    /** Media types this reader understands, e.g. {@code application/pdf}. */
    Set<String> supportedMediaTypes();

    /** @return the text, line breaks preserved; a failure when the file is unreadable or holds no text (e.g. a scan) */
    PluginResult<String> read(byte[] content, String mediaType);
}
