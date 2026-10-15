package com.jobsapply.platform.resumeimport;

import com.jobsapply.platform.catalog.CatalogService;
import com.jobsapply.platform.catalog.SkillExtractor;
import com.jobsapply.platform.plugin.PluginRegistry;
import com.jobsapply.platform.resumeimport.internal.TextMasking;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.spi.common.DegreeLevel;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.VerificationStatus;
import com.jobsapply.spi.profile.CandidateAchievement;
import com.jobsapply.spi.profile.CandidateContact;
import com.jobsapply.spi.profile.CandidateEducation;
import com.jobsapply.spi.profile.CandidateExperience;
import com.jobsapply.spi.profile.CandidateSkill;
import com.jobsapply.spi.profile.CandidateSnapshot;
import com.jobsapply.spi.resume.DocumentReader;
import com.jobsapply.spi.resume.ResumeExtraction;
import com.jobsapply.spi.resume.ResumeExtractor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Proposes a profile from a resume file (ADR-0015). The local reading always runs: it provides the contact details and
 * is the default extraction. An extractor that sends text externally receives the text with identifiers masked, and
 * only when the candidate chose it for this request. Nothing is stored.
 */
@Service
@RequiredArgsConstructor
public class ResumeImportService {

    public static final String LOCAL_EXTRACTOR = "heuristic-resume-extractor";
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");

    private final PluginRegistry<DocumentReader> readers;
    private final PluginRegistry<ResumeExtractor> extractors;
    private final CatalogService catalog;

    /** @param sendsTextExternally shown to the candidate before they choose it */
    public record ExtractorOption(String id, String label, boolean sendsTextExternally) {}

    public List<ExtractorOption> extractors() {
        return extractors.descriptors().stream().map(d -> extractors.require(d.id()))
                .map(e -> new ExtractorOption(e.descriptor().id(), e.label(), e.sendsTextExternally()))
                .sorted((a, b) -> Boolean.compare(a.sendsTextExternally(), b.sendsTextExternally()))
                .toList();
    }

    /**
     * @param extractorId {@code null} for the local extractor
     * @throws DomainRuleViolationException when the file cannot be read or the extractor is not available
     */
    public ProfileDraft draft(byte[] content, String declaredMediaType, String fileName, String extractorId) {
        UserContext.requireUserId();
        String mediaType = mediaType(content, declaredMediaType, fileName);
        DocumentReader reader = readers.descriptors().stream().map(d -> readers.require(d.id()))
                .filter(r -> r.supportedMediaTypes().contains(mediaType)).findFirst()
                .orElseThrow(() -> new DomainRuleViolationException("Upload a PDF, Word (.docx) or text file"));
        String text = value(reader.read(content, mediaType), "The file could not be read");

        ResumeExtraction local = value(extractors.require(LOCAL_EXTRACTOR).extract(text), "The resume could not be read");
        ResumeExtractor chosen = extractorId == null || extractorId.isBlank() ? extractors.require(LOCAL_EXTRACTOR)
                : extractors.find(extractorId).orElseThrow(() -> new DomainRuleViolationException("That extractor is not available"));
        ResumeExtraction extraction = local;
        if (chosen != extractors.require(LOCAL_EXTRACTOR)) {
            String input = chosen.sendsTextExternally() ? TextMasking.mask(text, local.contact() == null ? null : local.contact().fullName()) : text;
            ResumeExtraction remote = value(chosen.extract(input), chosen.label() + " could not read the resume");
            extraction = merge(local, remote);
        }
        return toDraft(extraction, chosen);
    }

    // --- internals -----------------------------------------------------------------------------------------------------

    private static <T> T value(PluginResult<T> result, String what) {
        return switch (result) {
            case PluginResult.Ok<T> ok -> ok.value();
            case PluginResult.Failed<T> f -> throw new DomainRuleViolationException(what + ": " + f.message());
            case PluginResult.Unsupported<T> u -> throw new DomainRuleViolationException(what + " (unsupported)");
        };
    }

    /** Trusts the file's own signature over the declared type; refuses what it cannot recognise. */
    static String mediaType(byte[] content, String declared, String fileName) {
        if (content == null || content.length == 0) {
            throw new DomainRuleViolationException("The file is empty");
        }
        if (startsWith(content, "%PDF-")) {
            return "application/pdf";
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (startsWith(content, "PK") && (name.endsWith(".docx") || DOCX.equals(declared))) {
            return DOCX;
        }
        if ((name.endsWith(".txt") || (declared != null && declared.startsWith("text/plain"))) && !startsWith(content, "PK")) {
            return "text/plain";
        }
        throw new DomainRuleViolationException("Upload a PDF, Word (.docx) or text file");
    }

    private static boolean startsWith(byte[] content, String prefix) {
        byte[] p = prefix.getBytes(StandardCharsets.US_ASCII);
        if (content.length < p.length) {
            return false;
        }
        for (int i = 0; i < p.length; i++) {
            if (content[i] != p[i]) {
                return false;
            }
        }
        return true;
    }

    /** Structure from the remote extraction; contact details from the local reading (they were masked remotely). */
    private static ResumeExtraction merge(ResumeExtraction local, ResumeExtraction remote) {
        ResumeExtraction.Contact l = local.contact();
        ResumeExtraction.Contact r = remote.contact();
        ResumeExtraction.Contact contact = new ResumeExtraction.Contact(
                l != null && l.fullName() != null ? l.fullName() : r == null ? null : r.fullName(),
                l == null ? null : l.email(), l == null ? null : l.phone(),
                r != null && r.city() != null ? r.city() : l == null ? null : l.city(),
                r != null && r.countryCode() != null ? r.countryCode() : l == null ? null : l.countryCode(),
                l == null ? List.of() : l.links());
        return new ResumeExtraction(contact, remote.experiences(), remote.education(), remote.skills(), remote.notes());
    }

    private ProfileDraft toDraft(ResumeExtraction x, ResumeExtractor extractor) {
        SkillExtractor skills = catalog.skillExtractor();
        List<String> notes = new ArrayList<>(x.notes());
        Set<String> usedIds = new HashSet<>();

        List<CandidateExperience> experiences = new ArrayList<>();
        List<CandidateAchievement> achievements = new ArrayList<>();
        Set<String> experienceSkills = new LinkedHashSet<>();
        for (ResumeExtraction.Experience e : x.experiences()) {
            String id = uniqueId("exp", e.employer() != null ? e.employer() : e.title(), usedIds);
            Set<String> roleSkills = new TreeSet<>(skills.extract(e.title()));
            int n = 0;
            for (String highlight : e.highlights()) {
                Set<String> found = new TreeSet<>(skills.extract(highlight));
                roleSkills.addAll(found);
                achievements.add(new CandidateAchievement(id + "-a" + (++n), VerificationStatus.UNVERIFIED, highlight, id, List.copyOf(found)));
            }
            experienceSkills.addAll(roleSkills);
            experiences.add(new CandidateExperience(id, VerificationStatus.UNVERIFIED, e.employer(), e.title(), e.start(),
                    e.current() ? null : e.end(), e.location(), List.copyOf(roleSkills)));
            if (e.title() == null || e.employer() == null || e.start() == null) {
                notes.add("Complete the " + (e.employer() != null ? "'" + e.employer() + "'" : "untitled") + " position: "
                        + missing(e.title() == null ? "title" : null, e.employer() == null ? "employer" : null, e.start() == null ? "start month" : null) + ".");
            }
        }

        List<CandidateEducation> education = new ArrayList<>();
        for (ResumeExtraction.Education e : x.education()) {
            DegreeLevel level = level(e.degree());
            education.add(new CandidateEducation(uniqueId("edu", e.institution() != null ? e.institution() : e.degree(), usedIds),
                    VerificationStatus.UNVERIFIED, e.institution(), level, e.field(), e.start(), e.end(), e.completed()));
            if (e.institution() == null || level == null) {
                notes.add("Complete the education entry " + (e.institution() != null ? "'" + e.institution() + "'" : "")
                        + ": " + missing(e.institution() == null ? "institution" : null, level == null ? "degree level" : null) + ".");
            }
        }

        Set<String> skillKeys = new TreeSet<>(experienceSkills);
        List<String> unknown = new ArrayList<>();
        for (String name : x.skills()) {
            Set<String> found = skills.extract(name);
            if (found.isEmpty()) {
                unknown.add(name);
            }
            skillKeys.addAll(found);
        }
        List<CandidateSkill> candidateSkills = skillKeys.stream()
                .map(key -> new CandidateSkill(uniqueId("sk", key, usedIds), VerificationStatus.UNVERIFIED, key, null, null)).toList();

        ResumeExtraction.Contact c = x.contact();
        CandidateContact contact = c == null ? null : new CandidateContact(c.fullName(),
                c.email() != null && EMAIL.matcher(c.email()).matches() ? c.email().toLowerCase(Locale.ROOT) : null,
                c.phone(), c.city(), c.countryCode(), links(c.links()));
        CandidateSnapshot snapshot = new CandidateSnapshot(contact, experiences, education, candidateSkills, achievements, List.of());
        return new ProfileDraft(snapshot, extractor.descriptor().id(), extractor.sendsTextExternally(), notes, unknown);
    }

    private static String missing(String... parts) {
        return String.join(", ", Arrays.stream(parts).filter(Objects::nonNull).toList());
    }

    private static List<URI> links(List<String> links) {
        List<URI> uris = new ArrayList<>();
        for (String link : links) {
            try {
                URI uri = URI.create(link);
                if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
                    uris.add(uri);
                }
            } catch (IllegalArgumentException ignored) {
                // not a usable link; left out
            }
        }
        return uris;
    }

    static DegreeLevel level(String degree) {
        if (degree == null) {
            return null;
        }
        String d = degree.toLowerCase(Locale.ROOT).replaceAll("[.\\s]", "");
        if (d.matches(".*(phd|doctor|dphil).*")) {
            return DegreeLevel.DOCTORATE;
        }
        if (d.matches("(master.*|msc|mtech|mba|ma|me|meng|mphil|ms)")) {
            return DegreeLevel.MASTER;
        }
        if (d.matches("(bachelor.*|bsc|btech|be|ba|beng|bs)")) {
            return DegreeLevel.BACHELOR;
        }
        if (d.startsWith("associate")) {
            return DegreeLevel.ASSOCIATE;
        }
        if (d.matches("(highschool.*|abitur|a-levels|alevels)")) {
            return DegreeLevel.HIGH_SCHOOL;
        }
        return null;
    }

    /** Fact ids like {@code exp-beta-ag}, unique within the draft. */
    private static String uniqueId(String prefix, String name, Set<String> used) {
        String slug = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        String base = prefix + (slug.isEmpty() ? "" : "-" + (slug.length() > 40 ? slug.substring(0, 40).replaceAll("-$", "") : slug));
        String id = base;
        for (int i = 2; !used.add(id); i++) {
            id = base + "-" + i;
        }
        return id;
    }
}
