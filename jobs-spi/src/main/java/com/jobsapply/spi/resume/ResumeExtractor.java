package com.jobsapply.spi.resume;

import com.jobsapply.spi.common.Plugin;
import com.jobsapply.spi.common.PluginResult;

/**
 * Turns resume text into structured, unconfirmed data. Must report only what the text states.
 *
 * @see ResumeExtraction
 */
public interface ResumeExtractor extends Plugin {

    /** Whether the text leaves this machine (e.g. sent to a hosted model). Shown to the candidate before use. */
    boolean sendsTextExternally();

    /** Short human-readable label, e.g. "On this machine". */
    String label();

    /**
     * @param text resume text; for extractors that send text externally the platform has already replaced e-mail
     *     addresses, phone numbers and links with placeholders (contact details are read locally)
     */
    PluginResult<ResumeExtraction> extract(String text);
}
