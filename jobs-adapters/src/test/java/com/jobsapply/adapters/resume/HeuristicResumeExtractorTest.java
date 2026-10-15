package com.jobsapply.adapters.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.resume.ResumeExtraction;
import com.jobsapply.spi.resume.ResumeExtraction.Education;
import com.jobsapply.spi.resume.ResumeExtraction.Experience;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** Fictional resumes in different layouts. What is stated is read; what is not stated stays empty. */
class HeuristicResumeExtractorTest {

    final HeuristicResumeExtractor extractor = new HeuristicResumeExtractor();

    static final String CLASSIC = """
            JANE EXAMPLE
            Senior Backend Engineer
            Berlin, Germany | jane.example@example.com | +49 30 1234 5678
            linkedin.com/in/jane-example | github.com/janeexample

            SUMMARY
            Backend engineer with 7 years of experience.

            EXPERIENCE
            Senior Backend Engineer | Beta AG | Berlin, Germany
            Jan 2023 – Present
            • Built an event pipeline on Kafka processing 2M events per day.
            • Led the migration to Spring Boot,
              reducing latency by 30%.
            Software Engineer, Example GmbH                      03/2019 - 12/2022
            • Developed REST APIs in Java and PostgreSQL.

            EDUCATION
            Example University
            B.Sc. in Computer Science, 2014 – 2018

            SKILLS
            Languages: Java, Kotlin, SQL
            Tools: Spring Boot, Kafka, PostgreSQL, Docker
            """;

    static final String NARRATIVE = """
            John Q Sample
            john.sample@example.org · (555) 123-4567 · https://johnsample.dev

            PROFESSIONAL EXPERIENCE
            Lead Data Scientist at Gamma Analytics — March 2020 to June 2024
            - Designed forecasting models in Python and pandas.
            Data Analyst at Delta Corp, Remote — 2017 – 2020
            - Built dashboards in SQL.

            EDUCATION
            Master of Science in Statistics, Sample Institute of Technology, 2017
            Bachelor of Arts in Mathematics — Sample College (2011 – 2015)
            M.Sc. in Data Science, Example University, expected 2026

            TECHNICAL SKILLS
            Python | SQL | pandas | TensorFlow
            """;

    private ResumeExtraction extract(String text) {
        PluginResult<ResumeExtraction> result = extractor.extract(text);
        assertThat(result).isInstanceOf(PluginResult.Ok.class);
        return ((PluginResult.Ok<ResumeExtraction>) result).value();
    }

    @Test
    void readsContactDetailsWithoutGuessingTheRest() {
        ResumeExtraction r = extract(CLASSIC);
        assertThat(r.contact().fullName()).isEqualTo("Jane Example");
        assertThat(r.contact().email()).isEqualTo("jane.example@example.com");
        assertThat(r.contact().phone()).isEqualTo("+49 30 1234 5678");
        assertThat(r.contact().links()).containsExactly("https://linkedin.com/in/jane-example", "https://github.com/janeexample");
        assertThat(r.contact().city()).as("never guessed").isNull();
        assertThat(r.contact().countryCode()).isNull();

        ResumeExtraction n = extract(NARRATIVE);
        assertThat(n.contact().fullName()).isEqualTo("John Q Sample");
        assertThat(n.contact().phone()).isEqualTo("(555) 123-4567");
        assertThat(n.contact().links()).containsExactly("https://johnsample.dev");
    }

    @Test
    void readsPositionsWithTitlesEmployersDatesAndHighlights() {
        ResumeExtraction r = extract(CLASSIC);
        assertThat(r.experiences()).hasSize(2);
        Experience current = r.experiences().get(0);
        assertThat(current.title()).isEqualTo("Senior Backend Engineer");
        assertThat(current.employer()).isEqualTo("Beta AG");
        assertThat(current.location()).isEqualTo("Berlin, Germany");
        assertThat(current.start()).isEqualTo(YearMonth.of(2023, 1));
        assertThat(current.end()).isNull();
        assertThat(current.current()).isTrue();
        assertThat(current.highlights()).containsExactly("Built an event pipeline on Kafka processing 2M events per day.",
                "Led the migration to Spring Boot, reducing latency by 30%.");
        Experience earlier = r.experiences().get(1);
        assertThat(earlier.title()).isEqualTo("Software Engineer");
        assertThat(earlier.employer()).isEqualTo("Example GmbH");
        assertThat(earlier.start()).isEqualTo(YearMonth.of(2019, 3));
        assertThat(earlier.end()).isEqualTo(YearMonth.of(2022, 12));
        assertThat(earlier.current()).isFalse();
    }

    @Test
    void handlesTitleAtEmployerLinesAndLeavesMissingMonthsEmpty() {
        ResumeExtraction n = extract(NARRATIVE);
        assertThat(n.experiences()).extracting(Experience::title).containsExactly("Lead Data Scientist", "Data Analyst");
        assertThat(n.experiences()).extracting(Experience::employer).containsExactly("Gamma Analytics", "Delta Corp");
        assertThat(n.experiences().get(1).location()).isEqualTo("Remote");
        assertThat(n.experiences().get(0).start()).isEqualTo(YearMonth.of(2020, 3));
        assertThat(n.experiences().get(0).end()).isEqualTo(YearMonth.of(2024, 6));
        assertThat(n.experiences().get(1).start()).as("only '2017' is written: no month is invented").isNull();
        assertThat(n.notes()).anySatisfy(note -> assertThat(note).contains("Delta Corp", "only the year it started (2017)"));
    }

    @Test
    void readsEducationWithDegreeFieldAndCompletion() {
        assertThat(extract(CLASSIC).education()).singleElement().satisfies(e -> {
            assertThat(e.institution()).isEqualTo("Example University");
            assertThat(e.degree()).isEqualTo("B.Sc.");
            assertThat(e.field()).isEqualTo("Computer Science");
            assertThat(e.completed()).isTrue();
        });
        assertThat(extract(NARRATIVE).education()).extracting(Education::institution, Education::degree, Education::field, Education::completed)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Sample Institute of Technology", "Master of Science", "Statistics", true),
                        org.assertj.core.groups.Tuple.tuple("Sample College", "Bachelor of Arts", "Mathematics", true),
                        org.assertj.core.groups.Tuple.tuple("Example University", "M.Sc.", "Data Science", false));
    }

    @Test
    void readsSkillListsAsWritten() {
        assertThat(extract(CLASSIC).skills()).containsExactly("Java", "Kotlin", "SQL", "Spring Boot", "Kafka", "PostgreSQL", "Docker");
        assertThat(extract(NARRATIVE).skills()).containsExactly("Python", "SQL", "pandas", "TensorFlow");
    }

    @Test
    void aResumeWithoutRecognisableSectionsYieldsNotesNotInventions() {
        ResumeExtraction r = extract("Alex Person\nalex@example.com\nI like building things and have done so for a while.\n");
        assertThat(r.experiences()).isEmpty();
        assertThat(r.education()).isEmpty();
        assertThat(r.notes()).anySatisfy(n -> assertThat(n).contains("No work-experience section"));
        assertThat(extractor.extract("  ")).isInstanceOf(PluginResult.Failed.class);
    }
}
