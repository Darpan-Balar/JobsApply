package com.jobsapply.adapters.render;

import com.jobsapply.spi.render.Claim;
import com.jobsapply.spi.render.ResumeDocument;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a self-contained, XHTML-compatible resume page. Every value is escaped: content can never inject markup or
 * script. Only the structure and inline CSS come from here.
 */
public final class ResumeHtml {

    private ResumeHtml() {}

    public static String render(ResumeDocument document) {
        StringBuilder html = new StringBuilder(4096);
        ResumeDocument.Header header = document.header();
        html.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"UTF-8\" />")
                .append("<title>").append(escape(header.name() == null ? "Resume" : header.name())).append("</title>")
                .append("<style>")
                .append("body{font-family:Helvetica,Arial,sans-serif;font-size:10.5pt;color:#111;margin:28px 36px;line-height:1.35}")
                .append("h1{font-size:18pt;margin:0 0 4px}.contact{color:#444;margin-bottom:14px}")
                .append("h2{font-size:11.5pt;text-transform:uppercase;letter-spacing:.06em;border-bottom:1px solid #999;margin:16px 0 6px;padding-bottom:2px}")
                .append("p.position{font-weight:bold;margin:8px 0 2px}ul{margin:0 0 4px 18px;padding:0}li{margin:1px 0}")
                .append("p.skills{margin:0}")
                .append("</style></head><body>");
        if (header.name() != null) {
            html.append("<h1>").append(escape(header.name())).append("</h1>");
        }
        List<String> contact = new ArrayList<>();
        for (String value : new String[] {header.email(), header.phone(), header.location()}) {
            if (value != null) {
                contact.add(escape(value));
            }
        }
        header.links().forEach(link -> contact.add(escape(link)));
        if (!contact.isEmpty()) {
            html.append("<div class=\"contact\">").append(String.join(" · ", contact)).append("</div>");
        }
        for (ResumeDocument.Section section : document.sections()) {
            html.append("<h2>").append(escape(section.heading())).append("</h2>");
            if ("Skills".equals(section.heading())) {
                html.append("<p class=\"skills\">")
                        .append(String.join(", ", section.claims().stream().map(c -> escape(c.text())).toList()))
                        .append("</p>");
                continue;
            }
            boolean open = false;
            for (Claim claim : section.claims()) {
                boolean position = "Experience".equals(section.heading()) && claim.sources().size() == 1 && claim.text().contains(" · ");
                if (position) {
                    if (open) {
                        html.append("</ul>");
                        open = false;
                    }
                    html.append("<p class=\"position\">").append(escape(claim.text())).append("</p>");
                } else {
                    if (!open) {
                        html.append("<ul>");
                        open = true;
                    }
                    html.append("<li>").append(escape(claim.text())).append("</li>");
                }
            }
            if (open) {
                html.append("</ul>");
            }
        }
        return html.append("</body></html>\n").toString();
    }

    public static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
