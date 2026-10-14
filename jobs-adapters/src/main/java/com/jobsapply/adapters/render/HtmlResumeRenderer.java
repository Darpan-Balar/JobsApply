package com.jobsapply.adapters.render;

import com.jobsapply.spi.common.ComplianceNote;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.PluginSettings;
import com.jobsapply.spi.render.DocumentRenderer;
import com.jobsapply.spi.render.RenderOptions;
import com.jobsapply.spi.render.RenderedDocument;
import com.jobsapply.spi.render.ResumeDocument;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** {@code html-resume}: a self-contained HTML page. */
public final class HtmlResumeRenderer implements DocumentRenderer {

    public static final String ID = "html-resume";

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder().id(ID).version("1.0.0").kind(PluginKind.DOCUMENT_RENDERER)
                .settingsType(PluginSettings.None.class).settingsSchemaVersion(1)
                .compliance(new ComplianceNote(null, false, "local rendering")).build();
    }

    @Override
    public Set<String> supportedMediaTypes() {
        return Set.of("text/html");
    }

    @Override
    public PluginResult<RenderedDocument> render(ResumeDocument document, RenderOptions options) {
        return PluginResult.ok(new RenderedDocument("text/html",
                ResumeHtml.render(document).getBytes(StandardCharsets.UTF_8), "resume.html"));
    }
}
