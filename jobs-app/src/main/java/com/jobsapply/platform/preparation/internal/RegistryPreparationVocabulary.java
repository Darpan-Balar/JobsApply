package com.jobsapply.platform.preparation.internal;

import com.jobsapply.platform.plugin.PluginRegistry;
import com.jobsapply.platform.preparation.PreparationVocabulary;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.render.CoverNoteGenerator;
import com.jobsapply.spi.render.DocumentRenderer;
import com.jobsapply.spi.render.ResumeContentGenerator;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RegistryPreparationVocabulary implements PreparationVocabulary {

    private final PluginRegistry<ResumeContentGenerator> generators;
    private final PluginRegistry<DocumentRenderer> renderers;
    private final PluginRegistry<CoverNoteGenerator> coverNotes;

    @Override
    public Set<String> contentGenerators() {
        return ids(generators);
    }

    @Override
    public Set<String> renderers() {
        return ids(renderers);
    }

    @Override
    public Set<String> coverNoteStrategies() {
        return ids(coverNotes);
    }

    private static Set<String> ids(PluginRegistry<?> registry) {
        return registry.descriptors().stream().map(PluginDescriptor::id).collect(Collectors.toUnmodifiableSet());
    }
}
