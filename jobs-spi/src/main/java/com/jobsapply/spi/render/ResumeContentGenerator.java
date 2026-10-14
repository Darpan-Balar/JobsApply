package com.jobsapply.spi.render;

import com.jobsapply.spi.common.Plugin;
import com.jobsapply.spi.matching.MatchContext;
import java.util.Map;

/**
 * Selects and phrases candidate facts for a job. Must not introduce statements that no fact supports: the platform
 * rejects any claim whose words or numbers do not appear in the facts it cites.
 */
public interface ResumeContentGenerator extends Plugin {

    /** @param skillNames canonical skill key → display name */
    ResumeDocument generate(MatchContext context, ResumeTemplateDefinition template, Map<String, String> skillNames);
}
