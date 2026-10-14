package com.jobsapply.platform.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.support.TestJobSourceAdapter;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.source.JobSourceAdapter;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

/** ADR-0003: the registry fails fast — and with it application startup — on any inconsistent plugin set. */
class PluginRegistryTest {

    private static final Validator VALIDATOR = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory().getValidator();

    private static PluginRegistry<JobSourceAdapter> registry(List<JobSourceAdapter> plugins, Set<String> disabled) {
        return new PluginRegistry<>(PluginKind.JOB_SOURCE, plugins, disabled, VALIDATOR);
    }

    /** Adapter whose descriptor can be replaced, to simulate faulty plugins. */
    static class CustomAdapter extends TestJobSourceAdapter {
        private final PluginDescriptor descriptor;

        CustomAdapter(PluginDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public PluginDescriptor descriptor() {
            return descriptor;
        }
    }

    @Test
    void registersAndLooksUpById() {
        PluginRegistry<JobSourceAdapter> registry = registry(List.of(new TestJobSourceAdapter("a-board"), new TestJobSourceAdapter("b-board")), Set.of());

        assertThat(registry.isRegistered("a-board")).isTrue();
        assertThat(registry.find("missing")).isEmpty();
        assertThat(registry.descriptors()).extracting(PluginDescriptor::id).containsExactly("a-board", "b-board");
        assertThatThrownBy(() -> registry.require("missing")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void duplicateIdsFailFastNamingBothImplementations() {
        assertThatThrownBy(() -> registry(List.of(new TestJobSourceAdapter("same"), new CustomAdapter(new TestJobSourceAdapter("same").descriptor())), Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate plugin id 'same'")
                .hasMessageContaining(TestJobSourceAdapter.class.getName())
                .hasMessageContaining(CustomAdapter.class.getName());
    }

    @Test
    void kindMismatchFailsFast() {
        PluginDescriptor wrongKind = new TestJobSourceAdapter().descriptor().toBuilder().kind(PluginKind.LLM_PROVIDER).build();
        assertThatThrownBy(() -> registry(List.of(new CustomAdapter(wrongKind)), Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declares kind LLM_PROVIDER but implements JOB_SOURCE");
    }

    @Test
    void invalidDescriptorFailsFast() {
        PluginDescriptor badId = new TestJobSourceAdapter().descriptor().toBuilder().id("Bad Id!").build();
        assertThatThrownBy(() -> registry(List.of(new CustomAdapter(badId)), Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid descriptor")
                .hasMessageContaining("id");
    }

    @Test
    void missingDescriptorFailsFast() {
        assertThatThrownBy(() -> registry(List.of(new CustomAdapter(null)), Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("returned no descriptor");
    }

    @Test
    void disabledPluginsAreNotRegistered() {
        PluginRegistry<JobSourceAdapter> registry = registry(List.of(new TestJobSourceAdapter("a-board"), new TestJobSourceAdapter("b-board")), Set.of("b-board"));
        assertThat(registry.descriptors()).extracting(PluginDescriptor::id).containsExactly("a-board");
    }
}
