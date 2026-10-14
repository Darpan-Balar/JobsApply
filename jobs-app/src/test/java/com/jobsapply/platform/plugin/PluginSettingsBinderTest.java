package com.jobsapply.platform.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.support.TestJobSourceAdapter;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.ValidationReport;
import jakarta.validation.Validation;
import java.time.Duration;
import java.util.Map;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

class PluginSettingsBinderTest {

    private final PluginSettingsBinder binder = new PluginSettingsBinder(new JsonCodec(), Validation.byDefaultProvider()
            .configure().messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory().getValidator());
    private final PluginDescriptor descriptor = new TestJobSourceAdapter().descriptor();

    @Test
    void bindsValidSettingsToTheTypedRecord() {
        PluginSettingsBinder.Binding binding = binder.bind(descriptor, Map.of("requestTimeout", "PT10S", "maxRetries", 3));
        assertThat(binding.report().isValid()).isTrue();
        assertThat(binding.settings()).isEqualTo(new TestJobSourceAdapter.Settings(Duration.ofSeconds(10), 3));
    }

    @Test
    void reportsConstraintViolationsWithPaths() {
        assertThat(binder.bind(descriptor, Map.of("requestTimeout", "PT10S", "maxRetries", 11)).report().issues())
                .containsExactly(new ValidationReport.Issue("maxRetries", "must be less than or equal to 10"));
        assertThat(binder.bind(descriptor, Map.of()).report().issues())
                .containsExactly(new ValidationReport.Issue("requestTimeout", "must not be null"));
    }

    @Test
    void rejectsUnknownKeysAndWrongTypes() {
        assertThat(binder.bind(descriptor, Map.of("requestTimeout", "PT10S", "retries", 3)).report().issues())
                .singleElement().satisfies(issue -> assertThat(issue.path()).isEqualTo("retries"));
        assertThat(binder.bind(descriptor, Map.of("requestTimeout", "ten seconds")).report().issues())
                .singleElement().satisfies(issue -> assertThat(issue.path()).isEqualTo("requestTimeout"));
    }
}
