package com.jobsapply.platform.matching.internal;

import com.jobsapply.platform.matching.MatchingVocabulary;
import com.jobsapply.platform.plugin.PluginRegistry;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.matching.EligibilityRule;
import com.jobsapply.spi.matching.MatchingStrategy;
import com.jobsapply.spi.matching.ScoreComponent;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The names configuration may use are exactly the installed plugins (replaces Phase 1's declared list). */
@Component
@RequiredArgsConstructor
class RegistryMatchingVocabulary implements MatchingVocabulary {

    private final PluginRegistry<EligibilityRule> rules;
    private final PluginRegistry<ScoreComponent> components;
    private final PluginRegistry<MatchingStrategy> strategies;

    @Override
    public Set<String> eligibilityRuleTypes() {
        return ids(rules);
    }

    @Override
    public Set<String> scoreComponents() {
        return ids(components);
    }

    @Override
    public Set<String> strategies() {
        return ids(strategies);
    }

    private static Set<String> ids(PluginRegistry<?> registry) {
        return registry.descriptors().stream().map(PluginDescriptor::id).collect(Collectors.toUnmodifiableSet());
    }
}
