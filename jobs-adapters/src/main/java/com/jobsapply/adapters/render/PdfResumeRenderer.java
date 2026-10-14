package com.jobsapply.adapters.render;

import com.jobsapply.spi.common.ComplianceNote;
import com.jobsapply.spi.common.FailureKind;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.PluginSettings;
import com.jobsapply.spi.render.DocumentRenderer;
import com.jobsapply.spi.render.RenderOptions;
import com.jobsapply.spi.render.RenderedDocument;
import com.jobsapply.spi.render.ResumeDocument;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;

/**
 * {@code pdf-resume}: the HTML layout printed to PDF (openhtmltopdf + PDFBox, both local). Uses the standard PDF fonts:
 * characters outside Latin-1 may not render — a limitation noted in the package warnings.
 */
public final class PdfResumeRenderer implements DocumentRenderer {

    public static final String ID = "pdf-resume";

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder().id(ID).version("1.0.0").kind(PluginKind.DOCUMENT_RENDERER)
                .settingsType(PluginSettings.None.class).settingsSchemaVersion(1)
                .compliance(new ComplianceNote(null, false, "local rendering (openhtmltopdf, LGPL-2.1, unmodified)")).build();
    }

    @Override
    public Set<String> supportedMediaTypes() {
        return Set.of("application/pdf");
    }

    @Override
    public PluginResult<RenderedDocument> render(ResumeDocument document, RenderOptions options) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var w3c = new W3CDom().fromJsoup(Jsoup.parse(ResumeHtml.render(document)));
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(w3c, null);
            builder.toStream(out);
            builder.run();
            return PluginResult.ok(new RenderedDocument("application/pdf", out.toByteArray(), "resume.pdf"));
        } catch (Exception e) {
            return PluginResult.failed(FailureKind.PERMANENT, "PDF rendering failed: " + e.getClass().getSimpleName());
        }
    }
}
