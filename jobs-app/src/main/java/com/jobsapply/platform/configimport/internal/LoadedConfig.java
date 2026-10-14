package com.jobsapply.platform.configimport.internal;

import com.jobsapply.platform.configimport.ConfigDocument;
import com.jobsapply.platform.configimport.ConfigIssue;
import com.jobsapply.platform.configimport.ConfigKind;
import java.util.List;

/** Documents that parsed and passed structural validation, plus the problems found in the others. */
public record LoadedConfig(List<ConfigDocument> documents, List<ConfigIssue> issues) {

    public LoadedConfig {
        documents = List.copyOf(documents);
        issues = List.copyOf(issues);
    }

    public List<ConfigDocument> ofKind(ConfigKind kind) {
        return documents.stream().filter(d -> d.kind() == kind).toList();
    }
}
