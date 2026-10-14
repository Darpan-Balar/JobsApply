package com.jobsapply.platform.plugin.internal;

import com.jobsapply.platform.plugin.PluginProperties;
import com.jobsapply.platform.plugin.PluginRegistry;
import com.jobsapply.spi.application.ApplicationAdapter;
import com.jobsapply.spi.common.Plugin;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.matching.EligibilityRule;
import com.jobsapply.spi.matching.MatchingStrategy;
import com.jobsapply.spi.matching.ScoreComponent;
import com.jobsapply.spi.render.AnswerDrafter;
import com.jobsapply.spi.render.CoverNoteGenerator;
import com.jobsapply.spi.render.DocumentRenderer;
import com.jobsapply.spi.render.ResumeContentGenerator;
import com.jobsapply.spi.source.JobSourceAdapter;
import jakarta.validation.Validator;
import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One registry per plugin kind in use. */
@Configuration(proxyBeanMethods = false)
class PluginRegistryConfiguration {

    @Bean
    PluginRegistry<JobSourceAdapter> jobSourceAdapters(
            ObjectProvider<JobSourceAdapter> adapters, PluginProperties properties, Validator validator) {
        return new PluginRegistry<>(PluginKind.JOB_SOURCE, adapters.orderedStream().toList(), properties.disabled(), validator);
    }

    @Bean
    PluginRegistry<EligibilityRule> eligibilityRules(
            ObjectProvider<EligibilityRule> rules, PluginProperties properties, Validator validator) {
        return named(PluginKind.ELIGIBILITY_RULE, rules.orderedStream().toList(), EligibilityRule::ruleType, properties, validator);
    }

    @Bean
    PluginRegistry<ScoreComponent> scoreComponents(
            ObjectProvider<ScoreComponent> components, PluginProperties properties, Validator validator) {
        return named(PluginKind.SCORE_COMPONENT, components.orderedStream().toList(), ScoreComponent::componentKey, properties, validator);
    }

    @Bean
    PluginRegistry<MatchingStrategy> matchingStrategies(
            ObjectProvider<MatchingStrategy> strategies, PluginProperties properties, Validator validator) {
        return named(PluginKind.MATCHING_STRATEGY, strategies.orderedStream().toList(), MatchingStrategy::strategyKey, properties, validator);
    }

    @Bean
    PluginRegistry<ResumeContentGenerator> resumeContentGenerators(
            ObjectProvider<ResumeContentGenerator> plugins, PluginProperties properties, Validator validator) {
        return new PluginRegistry<>(PluginKind.CONTENT_GENERATOR, plugins.orderedStream().toList(), properties.disabled(), validator);
    }

    @Bean
    PluginRegistry<CoverNoteGenerator> coverNoteGenerators(
            ObjectProvider<CoverNoteGenerator> plugins, PluginProperties properties, Validator validator) {
        return named(PluginKind.CONTENT_GENERATOR, plugins.orderedStream().toList(), CoverNoteGenerator::strategy, properties, validator);
    }

    @Bean
    PluginRegistry<DocumentRenderer> documentRenderers(
            ObjectProvider<DocumentRenderer> plugins, PluginProperties properties, Validator validator) {
        return new PluginRegistry<>(PluginKind.DOCUMENT_RENDERER, plugins.orderedStream().toList(), properties.disabled(), validator);
    }

    @Bean
    PluginRegistry<AnswerDrafter> answerDrafters(
            ObjectProvider<AnswerDrafter> plugins, PluginProperties properties, Validator validator) {
        return new PluginRegistry<>(PluginKind.ANSWER_DRAFTER, plugins.orderedStream().toList(), properties.disabled(), validator);
    }

    @Bean
    PluginRegistry<ApplicationAdapter> applicationAdapters(
            ObjectProvider<ApplicationAdapter> plugins, PluginProperties properties, Validator validator) {
        return new PluginRegistry<>(PluginKind.APPLICATION_ADAPTER, plugins.orderedStream().toList(), properties.disabled(), validator);
    }

    /** Role profiles reference these plugins by name, so the plugin id must equal that name (checked at startup). */
    private static <T extends Plugin> PluginRegistry<T> named(
            PluginKind kind, List<T> plugins, Function<T, String> name, PluginProperties properties, Validator validator) {
        for (T plugin : plugins) {
            String id = plugin.descriptor() == null ? null : plugin.descriptor().id();
            if (id != null && !id.equals(name.apply(plugin))) {
                throw new IllegalStateException(kind + " plugin " + plugin.getClass().getName() + " has id '" + id
                        + "' but is referenced as '" + name.apply(plugin) + "'; they must be equal");
            }
        }
        return new PluginRegistry<>(kind, plugins, properties.disabled(), validator);
    }
}
