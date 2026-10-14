package com.jobsapply.platform.catalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Finds catalog skills mentioned in free text (job titles and descriptions). Terms match case-insensitively on
 * boundaries that treat {@code + # .} as part of a word, so {@code c++}, {@code c#} and {@code node.js} work and
 * {@code java} does not match inside {@code javascript}. Immutable and thread-safe.
 */
public final class SkillExtractor {

    private final Pattern pattern;
    private final Map<String, String> termToSkill = new HashMap<>();

    SkillExtractor(Map<String, Set<String>> termsBySkill) {
        termsBySkill.forEach((skill, terms) -> terms.stream()
                .map(t -> t.strip().toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty())
                .forEach(t -> termToSkill.putIfAbsent(t, skill)));
        List<String> terms = new ArrayList<>(termToSkill.keySet());
        terms.sort(Comparator.comparingInt(String::length).reversed()); // longest first: "spring boot" before "spring"
        this.pattern = terms.isEmpty() ? null : Pattern.compile(
                "(?<![\\p{Alnum}+#.])(" + terms.stream().map(SkillExtractor::termRegex).collect(Collectors.joining("|"))
                        + ")(?![\\p{Alnum}+#]|\\.\\p{Alnum})",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** @return canonical skill keys mentioned in any of the texts */
    public Set<String> extract(String... texts) {
        Set<String> found = new HashSet<>();
        if (pattern == null) {
            return found;
        }
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                String skill = termToSkill.get(m.group(1).toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
                if (skill != null) {
                    found.add(skill);
                }
            }
        }
        return found;
    }

    /** Literal term; internal whitespace matches any whitespace run. */
    private static String termRegex(String term) {
        return Arrays.stream(term.split("\\s+")).map(Pattern::quote).collect(Collectors.joining("\\s+"));
    }
}
