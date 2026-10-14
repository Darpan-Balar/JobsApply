package com.jobsapply.spi.render;

import com.jobsapply.spi.common.Plugin;
import com.jobsapply.spi.matching.MatchContext;

/** Selects and phrases candidate facts for a job. Must not introduce statements that no fact supports. */
public interface ResumeContentGenerator extends Plugin {

    ResumeDocument generate(MatchContext context, ResumeTemplateDefinition template);
}
