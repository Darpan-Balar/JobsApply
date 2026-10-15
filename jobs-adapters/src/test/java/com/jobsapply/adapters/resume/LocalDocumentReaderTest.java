package com.jobsapply.adapters.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.adapters.render.PdfResumeRenderer;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.profile.FactRef;
import com.jobsapply.spi.render.Claim;
import com.jobsapply.spi.render.RenderOptions;
import com.jobsapply.spi.render.RenderedDocument;
import com.jobsapply.spi.render.ResumeDocument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class LocalDocumentReaderTest {

    final LocalDocumentReader reader = new LocalDocumentReader();

    private String ok(PluginResult<String> result) {
        assertThat(result).isInstanceOf(PluginResult.Ok.class);
        return ((PluginResult.Ok<String>) result).value();
    }

    @Test
    void readsTheTextOfARealPdf() {
        UUID version = UUID.randomUUID();
        ResumeDocument resume = new ResumeDocument("t", version, new ResumeDocument.Header("Jane Example", "jane@example.com", null, "Berlin", List.of()),
                List.of(new ResumeDocument.Section("Experience", List.of(new Claim("Senior Backend Engineer, Beta AG · Jan 2023 – Present",
                        List.of(new FactRef(version, "exp-1")), false)))));
        RenderedDocument pdf = ((PluginResult.Ok<RenderedDocument>) new PdfResumeRenderer().render(resume, new RenderOptions("application/pdf", null))).value();

        String text = ok(reader.read(pdf.content(), "application/pdf"));

        assertThat(text).contains("Jane Example", "jane@example.com", "Senior Backend Engineer, Beta AG", "Jan 2023 \u2013 Present");
        assertThat(text).containsIgnoringCase("Experience");
    }

    @Test
    void readsWordDocumentsAndPlainText() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<w:document><w:body><w:p><w:r><w:t>Jane Example</w:t></w:r></w:p>"
                    + "<w:p><w:r><w:t xml:space=\"preserve\">Backend &amp; data </w:t></w:r><w:r><w:t>engineer</w:t></w:r></w:p>"
                    + "</w:body></w:document>").getBytes(StandardCharsets.UTF_8));
        }
        assertThat(ok(reader.read(bytes.toByteArray(), LocalDocumentReader.DOCX))).isEqualTo("Jane Example\nBackend & data engineer\n");
        assertThat(ok(reader.read("Jane Example\r\nEngineer at Example".getBytes(StandardCharsets.UTF_8), "text/plain")))
                .isEqualTo("Jane Example\nEngineer at Example");
    }

    @Test
    void unreadableFilesFailClearly() {
        assertThat(reader.read("not a pdf at all".getBytes(StandardCharsets.UTF_8), "application/pdf")).isInstanceOf(PluginResult.Failed.class);
        assertThat(reader.read("short".getBytes(StandardCharsets.UTF_8), "text/plain")).isInstanceOfSatisfying(PluginResult.Failed.class,
                f -> assertThat(f.message()).contains("no readable text"));
        assertThat(reader.read(new byte[] {1, 2, 3}, "image/png")).isInstanceOf(PluginResult.Failed.class);
    }
}
