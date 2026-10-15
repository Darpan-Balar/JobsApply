package com.jobsapply.adapters.resume;

import com.jobsapply.spi.common.ComplianceNote;
import com.jobsapply.spi.common.FailureKind;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.PluginSettings;
import com.jobsapply.spi.resume.DocumentReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Reads PDF (PDFBox), DOCX (its {@code word/document.xml}, no extra library) and plain text, entirely locally. A PDF
 * without a text layer (a scan) is reported as such rather than returned as empty text.
 */
public final class LocalDocumentReader implements DocumentReader {

    public static final String ID = "local-document-reader";
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final int MAX_DOCX_XML_BYTES = 20 * 1024 * 1024;
    private static final Pattern PARAGRAPH = Pattern.compile("<w:p[ >].*?</w:p>|<w:p/>", Pattern.DOTALL);
    private static final Pattern RUN_TEXT = Pattern.compile("<w:t(?: [^>]*)?>([^<]*)</w:t>|<w:tab/>|<w:br/>");

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder().id(ID).version("1.0.0").kind(PluginKind.DOCUMENT_READER)
                .settingsType(PluginSettings.None.class).settingsSchemaVersion(1)
                .compliance(new ComplianceNote(null, false, "local text extraction (PDFBox, Apache-2.0)")).build();
    }

    @Override
    public Set<String> supportedMediaTypes() {
        return Set.of("application/pdf", DOCX, "text/plain");
    }

    @Override
    public PluginResult<String> read(byte[] content, String mediaType) {
        try {
            String text = switch (mediaType == null ? "" : mediaType) {
                case "application/pdf" -> pdf(content);
                case DOCX -> docx(content);
                case "text/plain" -> new String(content, StandardCharsets.UTF_8);
                default -> null;
            };
            if (text == null) {
                return PluginResult.failed(FailureKind.PERMANENT, "unsupported file type");
            }
            text = text.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ');
            if (text.strip().length() < 20) {
                return PluginResult.failed(FailureKind.PERMANENT, "the file contains no readable text (is it a scanned image?)");
            }
            return PluginResult.ok(text);
        } catch (IOException | RuntimeException e) {
            return PluginResult.failed(FailureKind.PERMANENT, "the file could not be read (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static String pdf(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            return stripper.getText(document);
        }
    }

    private static String docx(byte[] content) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if ("word/document.xml".equals(entry.getName())) {
                    byte[] xml = zip.readNBytes(MAX_DOCX_XML_BYTES);
                    return paragraphs(new String(xml, StandardCharsets.UTF_8));
                }
            }
        }
        throw new IOException("not a Word document");
    }

    private static String paragraphs(String xml) {
        StringBuilder text = new StringBuilder();
        Matcher paragraph = PARAGRAPH.matcher(xml);
        while (paragraph.find()) {
            Matcher run = RUN_TEXT.matcher(paragraph.group());
            while (run.find()) {
                text.append(run.group(1) != null ? unescape(run.group(1)) : run.group().startsWith("<w:tab") ? "\t" : "\n");
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }
}
