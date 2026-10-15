package com.jobsapply.adapters.resume;

import com.jobsapply.spi.common.ComplianceNote;
import com.jobsapply.spi.common.FailureKind;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.PluginSettings;
import com.jobsapply.spi.resume.ResumeExtraction;
import com.jobsapply.spi.resume.ResumeExtraction.Contact;
import com.jobsapply.spi.resume.ResumeExtraction.Education;
import com.jobsapply.spi.resume.ResumeExtraction.Experience;
import com.jobsapply.spi.resume.ResumeExtractor;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a resume with layout heuristics, on this machine. Reliable for contact details, section structure, date ranges
 * and skills lists; job titles and employers are best-effort (the candidate reviews everything). Never guesses: a
 * year without a month leaves the month empty, an unclear title/employer split is reported in the notes.
 */
public final class HeuristicResumeExtractor implements ResumeExtractor {

    public static final String ID = "heuristic-resume-extractor";

    private enum Section { HEADER, EXPERIENCE, EDUCATION, SKILLS, OTHER }

    private static final Map<Section, Set<String>> HEADINGS = Map.of(
            Section.EXPERIENCE, Set.of("experience", "work experience", "professional experience", "employment", "employment history",
                    "work history", "career history", "relevant experience", "professional background", "career"),
            Section.EDUCATION, Set.of("education", "academic background", "education and training", "academic qualifications",
                    "qualifications", "education & training"),
            Section.SKILLS, Set.of("skills", "technical skills", "core skills", "key skills", "technologies", "tech stack", "competencies",
                    "core competencies", "skills and tools", "skills & tools", "tools", "technical expertise", "expertise"),
            Section.OTHER, Set.of("summary", "profile", "professional summary", "about", "about me", "objective", "projects",
                    "personal projects", "certifications", "certificates", "awards", "achievements", "publications", "languages",
                    "interests", "hobbies", "references", "volunteering", "volunteer experience", "courses", "training"));

    private static final String MONTH = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|"
            + "sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\.?";
    private static final String DATE = "(?:" + MONTH + "\\s*,?\\s*((?:19|20)\\d{2})|(0?[1-9]|1[0-2])\\s*[/.]\\s*((?:19|20)\\d{2})"
            + "|((?:19|20)\\d{2})\\s*[/.-]\\s*(0[1-9]|1[0-2])(?!\\d)|((?:19|20)\\d{2}))";
    private static final String OPEN_END = "(present|current|now|today|ongoing|to date|date)";
    private static final Pattern RANGE = Pattern.compile("(?i)" + DATE + "\\s*(?:-|–|—|to|until|till)\\s*(?:" + DATE + "|" + OPEN_END + ")");
    private static final Pattern SINGLE_DATE = Pattern.compile("(?i)" + DATE);
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w/])\\+?\\(?\\d[\\d\\s().-]{6,}\\d(?![\\w/])");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://)?(?:www\\.)?((?:linkedin\\.com/in|github\\.com|gitlab\\.com)/[A-Za-z0-9_.-]+"
            + "|[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:dev|io|me|com|net|org)(?:/[^\\s,;|]*)?)");
    private static final Pattern BULLET = Pattern.compile("^\\s*(?:[•●▪◦‣∙·*–—-]|\\d+[.)])\\s+");
    private static final Pattern TITLE_WORDS = Pattern.compile("(?i)\\b(engineer|developer|programmer|manager|lead|head|director|analyst|"
            + "scientist|architect|consultant|intern|designer|specialist|administrator|officer|associate|coordinator|founder|cto|ceo|"
            + "vp|president|researcher|technician|tester|qa|devops|sre|owner|advisor|teacher|lecturer|assistant|executive|accountant|"
            + "recruiter|writer|editor|nurse|product)\\b");
    private static final Pattern INSTITUTION_WORDS = Pattern.compile("(?i)\\b(university|universität|college|institute|school|academy|"
            + "polytechnic|hochschule|iit|nit|mit|école|ecole|faculty)\\b");
    private static final Pattern DEGREE = Pattern.compile("(?i)\\b(ph\\.?\\s?d\\.?|doctor(?:ate)? of [a-z]+|master of [a-z ]+?(?= in | of |,|$)"
            + "|bachelor of [a-z ]+?(?= in | of |,|$)|m\\.?\\s?sc\\.?|b\\.?\\s?sc\\.?|m\\.?\\s?tech|b\\.?\\s?tech|b\\.?\\s?e\\.|m\\.?\\s?e\\.|mba|"
            + "m\\.?a\\.|b\\.?a\\.|bachelor'?s?|master'?s?|associate'?s? degree|diploma|high school(?: diploma)?|abitur|a-levels)");
    private static final Pattern FIELD_AFTER_DEGREE = Pattern.compile("(?i)^\\s*(?:degree\\s+)?(?:in|of)\\s+([A-Za-z&,/ .'-]{3,80}?)(?=\\s*(?:[,|–—(]|-\\s|$|\\d))");
    private static final Pattern NOT_COMPLETED = Pattern.compile("(?i)\\b(expected|anticipated|pursuing|candidate|in progress|ongoing|present|current)\\b");
    private static final Pattern REMOTE_OR_PLACE = Pattern.compile("(?i)^(remote|hybrid|on-?site)$|^[A-Z][\\p{L} .'-]+,\\s*[A-Z][\\p{L} .'-]+$");

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder().id(ID).version("1.0.0").kind(PluginKind.RESUME_EXTRACTOR)
                .settingsType(PluginSettings.None.class).settingsSchemaVersion(1)
                .compliance(new ComplianceNote(null, false, "runs locally; nothing leaves this machine")).build();
    }

    @Override
    public boolean sendsTextExternally() {
        return false;
    }

    @Override
    public String label() {
        return "On this machine";
    }

    @Override
    public PluginResult<ResumeExtraction> extract(String text) {
        if (text == null || text.isBlank()) {
            return PluginResult.failed(FailureKind.PERMANENT, "no text to read");
        }
        List<String> lines = text.lines().map(l -> l.replace('\t', ' ').replaceAll("\\s{2,}", "  ").strip()).filter(l -> !l.isEmpty()).toList();
        Map<Section, List<String>> sections = split(lines);
        List<String> notes = new ArrayList<>();
        Contact contact = contact(sections.getOrDefault(Section.HEADER, List.of()), lines);
        List<Experience> experiences = experiences(sections.getOrDefault(Section.EXPERIENCE, List.of()), notes);
        List<Education> education = education(sections.getOrDefault(Section.EDUCATION, List.of()), notes);
        List<String> skills = skills(sections.getOrDefault(Section.SKILLS, List.of()));
        if (!sections.containsKey(Section.EXPERIENCE)) {
            notes.add("No work-experience section was recognised; add your positions in the editor.");
        }
        if (!sections.containsKey(Section.EDUCATION)) {
            notes.add("No education section was recognised.");
        }
        return PluginResult.ok(new ResumeExtraction(contact, experiences, education, skills, notes));
    }

    // --- sections ------------------------------------------------------------------------------------------------------

    private static Map<Section, List<String>> split(List<String> lines) {
        Map<Section, List<String>> sections = new LinkedHashMap<>();
        Section current = Section.HEADER;
        for (String line : lines) {
            Optional<Section> heading = heading(line);
            if (heading.isPresent()) {
                current = heading.get();
                sections.computeIfAbsent(current, s -> new ArrayList<>());
                continue;
            }
            sections.computeIfAbsent(current, s -> new ArrayList<>()).add(line);
        }
        return sections;
    }

    static Optional<Section> heading(String line) {
        if (line.length() > 40 || BULLET.matcher(line).find()) {
            return Optional.empty();
        }
        String key = line.toLowerCase(Locale.ROOT).replaceAll("[:|_*#]+", " ").replaceAll("\\s+", " ").strip();
        return HEADINGS.entrySet().stream().filter(e -> e.getValue().contains(key)).map(Map.Entry::getKey).findFirst();
    }

    // --- contact -------------------------------------------------------------------------------------------------------

    private static Contact contact(List<String> header, List<String> all) {
        List<String> top = header.isEmpty() ? all.subList(0, Math.min(8, all.size())) : header;
        String joined = String.join("\n", top);
        String email = first(EMAIL, joined).or(() -> first(EMAIL, String.join("\n", all))).orElse(null);
        String phone = phone(joined).orElse(null);
        Set<String> links = new LinkedHashSet<>();
        Matcher url = URL.matcher(joined.replace(email == null ? "\u0000" : email, " "));
        while (url.find()) {
            String found = url.group(1);
            if (found.matches("(?i)(linkedin\\.com/in|github\\.com|gitlab\\.com)/.+") || url.group().toLowerCase(Locale.ROOT).startsWith("http")) {
                links.add("https://" + found.replaceFirst("(?i)^(www\\.)", ""));
            }
        }
        String name = top.stream().limit(5).filter(HeuristicResumeExtractor::looksLikeName).findFirst().map(HeuristicResumeExtractor::titleCase)
                .orElse(null);
        return new Contact(name, email, phone, null, null, List.copyOf(links));
    }

    private static Optional<String> phone(String text) {
        Matcher m = PHONE.matcher(text);
        while (m.find()) {
            String candidate = m.group().strip();
            String digits = candidate.replaceAll("\\D", "");
            if (digits.length() >= 8 && digits.length() <= 15 && !RANGE.matcher(candidate).find() && !candidate.matches(".*\\b(19|20)\\d{2}\\s*[-–]\\s*(19|20)\\d{2}.*")) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean looksLikeName(String line) {
        if (line.length() > 50 || line.matches(".*[\\d@/:].*") || heading(line).isPresent() || TITLE_WORDS.matcher(line).find()) {
            return false;
        }
        String[] words = line.split("\\s+");
        if (words.length < 2 || words.length > 4) {
            return false;
        }
        for (String w : words) {
            if (!w.matches("\\p{Lu}[\\p{L}'.-]*")) {
                return false;
            }
        }
        return true;
    }

    private static String titleCase(String name) {
        if (!name.equals(name.toUpperCase(Locale.ROOT))) {
            return name;
        }
        StringBuilder out = new StringBuilder();
        for (String w : name.toLowerCase(Locale.ROOT).split("\\s+")) {
            out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    // --- experience ----------------------------------------------------------------------------------------------------

    private static final class Draft {
        final List<String> header = new ArrayList<>();
        final List<String> highlights = new ArrayList<>();
        DateValue start;
        DateValue end;
        boolean current;
        boolean lastWasBullet;
    }

    private static List<Experience> experiences(List<String> lines, List<String> notes) {
        List<Draft> drafts = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        Draft draft = null;
        for (String line : lines) {
            Matcher range = RANGE.matcher(line);
            boolean bullet = BULLET.matcher(line).find();
            if (!bullet && range.find()) {
                draft = new Draft();
                drafts.add(draft);
                String rest = (line.substring(0, range.start()) + " " + line.substring(range.end())).replaceAll("^[\\s|,–—-]+|[\\s|,–—(-]+$", "").strip();
                draft.header.addAll(pending.subList(Math.max(0, pending.size() - 2), pending.size()));
                pending.clear();
                if (!rest.isEmpty()) {
                    draft.header.add(rest);
                }
                draft.start = DateValue.parse(range, 0);
                draft.end = DateValue.parse(range, 8);
                draft.current = range.group(15) != null;
                continue;
            }
            if (bullet) {
                String text = BULLET.matcher(line).replaceFirst("").strip();
                if (draft != null && !text.isEmpty()) {
                    draft.highlights.add(text);
                    draft.lastWasBullet = true;
                }
                continue;
            }
            if (draft != null && draft.lastWasBullet && !draft.highlights.isEmpty() && Character.isLowerCase(line.charAt(0))) {
                int last = draft.highlights.size() - 1;
                draft.highlights.set(last, draft.highlights.get(last) + " " + line);
                continue;
            }
            if (draft != null && draft.header.size() < 2 && draft.highlights.isEmpty() && pending.isEmpty() && line.length() < 70) {
                draft.header.add(line);
                continue;
            }
            if (draft != null && (line.length() >= 70 || line.endsWith("."))) {
                draft.highlights.add(line);
                draft.lastWasBullet = false;
                continue;
            }
            pending.add(line);
        }
        List<Experience> result = new ArrayList<>();
        for (Draft d : drafts) {
            String[] titleEmployerLocation = titleEmployer(d.header);
            String title = titleEmployerLocation[0];
            String employer = titleEmployerLocation[1];
            String label = employer != null ? employer : title != null ? title : "a position";
            if (titleEmployerLocation[3] != null) {
                notes.add("Check the job title and employer of '" + label + "': the split was a guess from the layout.");
            }
            if (d.start != null && d.start.month == null) {
                notes.add("'" + label + "' gives only the year it started (" + d.start.year + "); add the month.");
            }
            if (d.end != null && d.end.month == null) {
                notes.add("'" + label + "' gives only the year it ended (" + d.end.year + "); add the month.");
            }
            result.add(new Experience(title, employer, titleEmployerLocation[2], d.start == null ? null : d.start.yearMonth(),
                    d.end == null ? null : d.end.yearMonth(), d.current, d.highlights));
        }
        return result;
    }

    /** @return title, employer, location, and a non-null fourth element when the split is uncertain */
    private static String[] titleEmployer(List<String> header) {
        List<String> parts = new ArrayList<>();
        String location = null;
        for (String h : header) {
            String[] at = h.split("(?i)\\s+(?:at|@)\\s+", 2);
            if (at.length == 2) {
                String employer = at[1].strip();
                String[] tail = employer.split(",\\s+");
                if (tail.length > 1 && REMOTE_OR_PLACE.matcher(tail[tail.length - 1]).find()) {
                    return withLocation(at[0].strip(), employer.substring(0, employer.lastIndexOf(',')).strip(), tail[tail.length - 1], false);
                }
                return withLocation(at[0].strip(), employer, null, false);
            }
            for (String strong : h.split("\\s+[|·•]\\s+|\\s+[–—]\\s+|\\s+-\\s+")) {
                String s = strong.strip();
                if (s.isEmpty()) {
                    continue;
                }
                if (location == null && REMOTE_OR_PLACE.matcher(s).find() && !TITLE_WORDS.matcher(s).find() && !parts.isEmpty()) {
                    location = s;
                    continue;
                }
                for (String piece : s.split(",\\s+(?=\\p{Lu})")) {
                    String p = piece.strip();
                    if (location == null && p.matches("(?i)remote|hybrid|on-?site")) {
                        location = p;
                    } else if (!p.isEmpty()) {
                        parts.add(p);
                    }
                }
            }
        }
        if (parts.isEmpty()) {
            return new String[] {null, null, location, null};
        }
        if (parts.size() == 1) {
            String only = parts.getFirst();
            return TITLE_WORDS.matcher(only).find() ? new String[] {only, null, location, null} : new String[] {null, only, location, null};
        }
        String a = parts.get(0);
        String b = parts.get(1);
        boolean aTitle = TITLE_WORDS.matcher(a).find();
        boolean bTitle = TITLE_WORDS.matcher(b).find();
        if (aTitle && !bTitle) {
            return withLocation(a, b, location, false);
        }
        if (bTitle && !aTitle) {
            return withLocation(b, a, location, false);
        }
        return withLocation(a, b, location, true);
    }

    private static String[] withLocation(String title, String employer, String location, boolean uncertain) {
        return new String[] {title, employer, location, uncertain ? "uncertain" : null};
    }

    /** A parsed date; {@code month} is null when only the year was written. */
    private record DateValue(int year, Integer month) {

        YearMonth yearMonth() {
            return month == null ? null : YearMonth.of(year, month);
        }

        /** Groups of DATE: 1 month name + 2 year | 3 month + 4 year | 5 year + 6 month | 7 year; second DATE offset by 7. */
        static DateValue parse(Matcher m, int offset) {
            int base = offset == 0 ? 0 : 7;
            if (m.group(base + 2) != null) {
                return new DateValue(Integer.parseInt(m.group(base + 2)), monthNumber(m.group(base + 1)));
            }
            if (m.group(base + 4) != null) {
                return new DateValue(Integer.parseInt(m.group(base + 4)), Integer.parseInt(m.group(base + 3)));
            }
            if (m.group(base + 5) != null) {
                return new DateValue(Integer.parseInt(m.group(base + 5)), Integer.parseInt(m.group(base + 6)));
            }
            if (m.group(base + 7) != null) {
                return new DateValue(Integer.parseInt(m.group(base + 7)), null);
            }
            return null;
        }

        static Integer monthNumber(String name) {
            String n = name.toLowerCase(Locale.ROOT);
            String[] months = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
            for (int i = 0; i < months.length; i++) {
                if (n.startsWith(months[i])) {
                    return i + 1;
                }
            }
            return null;
        }
    }

    // --- education -----------------------------------------------------------------------------------------------------

    private static List<Education> education(List<String> lines, List<String> notes) {
        List<List<String>> blocks = new ArrayList<>();
        List<String> block = null;
        boolean hasInstitution = false;
        boolean hasDegree = false;
        for (String line : lines) {
            String text = BULLET.matcher(line).replaceFirst("");
            boolean institution = INSTITUTION_WORDS.matcher(text).find();
            boolean degree = DEGREE.matcher(text).find();
            if (block == null || (institution && hasInstitution) || (degree && hasDegree && !institution)) {
                block = new ArrayList<>();
                blocks.add(block);
                hasInstitution = false;
                hasDegree = false;
            }
            block.add(text);
            hasInstitution |= institution;
            hasDegree |= degree;
        }
        List<Education> result = new ArrayList<>();
        for (List<String> b : blocks) {
            String joined = String.join(" | ", b);
            String institution = b.stream().flatMap(l -> Arrays.stream(stripDates(l).split("\\s*[,|–—()]\\s*")))
                    .map(String::strip).filter(piece -> INSTITUTION_WORDS.matcher(piece).find() && !DEGREE.matcher(piece).lookingAt())
                    .findFirst().orElse(null);
            if (institution == null && !DEGREE.matcher(joined).find()) {
                continue;
            }
            String degree = null;
            String field = null;
            Matcher d = DEGREE.matcher(joined);
            if (d.find()) {
                degree = d.group().strip();
                Matcher f = FIELD_AFTER_DEGREE.matcher(joined.substring(d.end()));
                if (f.find()) {
                    field = f.group(1).strip();
                }
            }
            DateValue start = null;
            DateValue end = null;
            Matcher range = RANGE.matcher(joined);
            boolean ongoing = false;
            if (range.find()) {
                start = DateValue.parse(range, 0);
                end = DateValue.parse(range, 8);
                ongoing = range.group(15) != null;
            } else {
                Matcher single = SINGLE_DATE.matcher(joined);
                DateValue last = null;
                while (single.find()) {
                    last = DateValue.parse(single, 0);
                }
                end = last;
            }
            Boolean completed = NOT_COMPLETED.matcher(joined).find() || ongoing ? Boolean.FALSE : end != null ? Boolean.TRUE : null;
            String label = institution != null ? institution : degree;
            if ((start != null && start.month == null) || (end != null && end.month == null)) {
                notes.add("'" + label + "' gives only years; add the months if you know them.");
            }
            result.add(new Education(institution, degree, field, start == null ? null : start.yearMonth(), end == null ? null : end.yearMonth(),
                    completed));
        }
        return result;
    }

    private static String stripDates(String line) {
        return RANGE.matcher(line).replaceAll(" ").replaceAll("\\b(19|20)\\d{2}\\b", " ").replaceAll("[\\s,|–—()-]+$", "").strip();
    }

    // --- skills --------------------------------------------------------------------------------------------------------

    private static List<String> skills(List<String> lines) {
        Set<String> skills = new LinkedHashSet<>();
        for (String line : lines) {
            String text = BULLET.matcher(line).replaceFirst("");
            int colon = text.indexOf(':');
            if (colon > 0 && colon < 40) {
                text = text.substring(colon + 1);
            }
            for (String token : text.split("[,;|•·]|\\s+/\\s+|\\s{2,}|\\s+-\\s+")) {
                String t = token.replaceAll("^[\\s()]+|[\\s().]+$", "").strip();
                if (t.length() >= 1 && t.length() <= 40) {
                    skills.add(t);
                }
            }
        }
        return List.copyOf(skills);
    }

    private static Optional<String> first(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? Optional.of(m.group()) : Optional.empty();
    }
}
