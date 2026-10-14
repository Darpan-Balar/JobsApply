package com.jobsapply.spi.render;

import com.jobsapply.spi.common.Lists;
import java.util.Map;

/** @param rendererId plugin id of the {@link DocumentRenderer} to use */
public record ResumeTemplateDefinition(String key, String rendererId, Map<String, Object> layout) {

    public ResumeTemplateDefinition {
        layout = Lists.immutable(layout);
    }
}
