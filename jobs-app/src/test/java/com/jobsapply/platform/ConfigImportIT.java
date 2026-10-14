package com.jobsapply.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.configimport.ConfigImportReport;
import com.jobsapply.platform.configimport.ConfigImportService;
import com.jobsapply.platform.configimport.ConfigItemOutcome;
import com.jobsapply.platform.configimport.ConfigKind;
import com.jobsapply.platform.support.IntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The shipped configuration validates and imports idempotently (ADR-0004). */
@IntegrationTest
class ConfigImportIT {

    /** Surefire/Failsafe run with the module directory as working directory. */
    static final Path REPO_CONFIG = Path.of("..", "config");

    @Autowired ConfigImportService importService;
    @Autowired JdbcTemplate jdbc;

    @TempDir Path temp;

    @Test
    void shippedConfigurationIsValid() {
        ConfigImportReport report = importService.validate(REPO_CONFIG);

        assertThat(report.issues()).isEmpty();
        assertThat(report.notices()).containsExactlyInAnyOrder(
                "source 'greenhouse-public' stays disabled: adapter 'greenhouse-job-board' is not installed",
                "source 'lever-public' stays disabled: adapter 'lever-postings' is not installed",
                "source 'ashby-public' stays disabled: adapter 'ashby-job-board' is not installed");
    }

    @Test
    void sourcesEmployersAndBindingsAreImported() throws IOException {
        ConfigImportReport report = importService.importDirectory(copyOf(REPO_CONFIG), false);

        assertThat(keys(report, ConfigKind.JOB_SOURCE)).containsExactlyInAnyOrder("greenhouse-public", "lever-public", "ashby-public");
        assertThat(keys(report, ConfigKind.EMPLOYER_SET)).containsExactlyInAnyOrder("example-fintech", "example-trading");
        assertThat(jdbc.queryForObject("select count(*) from job_source where key in ('greenhouse-public','lever-public','ashby-public') "
                + "and not enabled", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForList("select s.key || '/' || b.external_ref from employer_source b join employer e on e.id = b.employer_id "
                + "join job_source s on s.id = b.job_source_id where e.key = 'example-fintech' order by 1", String.class))
                .containsExactly("greenhouse-public/examplefintech", "lever-public/examplefintech");
    }

    @Test
    void importIsIdempotentAndVersionsOnlyRealChanges() throws IOException {
        Path config = copyOf(REPO_CONFIG);

        ConfigImportReport first = importService.importDirectory(config, false);
        assertThat(first.issues()).isEmpty();
        assertThat(first.applied()).isTrue();
        assertThat(keys(first, ConfigKind.ROLE_PROFILE)).containsExactlyInAnyOrder("backend-engineer", "quant-developer", "data-engineer");

        ConfigImportReport second = importService.importDirectory(config, false);
        assertThat(second.outcomes()).extracting(ConfigItemOutcome::kind).contains(ConfigKind.JOB_SOURCE, ConfigKind.EMPLOYER_SET);
        assertThat(second.outcomes()).as("re-importing identical files changes nothing")
                .allSatisfy(o -> assertThat(o.outcome()).startsWith("UNCHANGED"));

        Path backend = config.resolve("role-profiles/backend-engineer.yaml");
        String original = Files.readString(backend);
        int versionBefore = activeVersionNo("backend-engineer");

        Files.writeString(backend, original.replace("shortlist: 70", "shortlist: 72"));
        ConfigImportReport changed = importService.importDirectory(config, false);
        assertThat(changed.outcomes()).filteredOn(o -> !o.outcome().startsWith("UNCHANGED"))
                .singleElement()
                .satisfies(o -> {
                    assertThat(o.key()).isEqualTo("backend-engineer");
                    assertThat(o.outcome()).isEqualTo("NEW_VERSION (v" + (versionBefore + 1) + ")");
                });
        assertThat(activeVersionNo("backend-engineer")).isEqualTo(versionBefore + 1);

        Files.writeString(backend, original);
        ConfigImportReport reverted = importService.importDirectory(config, false);
        assertThat(reverted.outcomes()).filteredOn(o -> o.kind() == ConfigKind.ROLE_PROFILE && o.key().equals("backend-engineer"))
                .singleElement().satisfies(o -> assertThat(o.outcome()).startsWith("REACTIVATED"));
        assertThat(activeVersionNo("backend-engineer")).isEqualTo(versionBefore);
    }

    @Test
    void dryRunReportsChangesWithoutWritingThem() throws IOException {
        Path config = copyOf(REPO_CONFIG);
        importService.importDirectory(config, false);
        Path quant = config.resolve("role-profiles/quant-developer.yaml");
        Files.writeString(quant, Files.readString(quant).replace("shortlist: 75", "shortlist: 76"));
        int versionsBefore = versionCount("quant-developer");

        ConfigImportReport dryRun = importService.importDirectory(config, true);

        assertThat(dryRun.dryRun()).isTrue();
        assertThat(dryRun.applied()).isFalse();
        assertThat(dryRun.outcomes()).filteredOn(o -> o.kind() == ConfigKind.ROLE_PROFILE && o.key().equals("quant-developer"))
                .singleElement().satisfies(o -> assertThat(o.outcome()).startsWith("NEW_VERSION"));
        assertThat(versionCount("quant-developer")).isEqualTo(versionsBefore);
    }

    @Test
    void invalidConfigurationIsReportedAndNothingIsWritten() throws IOException {
        Path config = copyOf(REPO_CONFIG);
        importService.importDirectory(config, false);
        Path data = config.resolve("role-profiles/data-engineer.yaml");
        Files.writeString(data, Files.readString(data)
                .replace("shortlist: 70", "shortlist: 71")      // a real change that must NOT be applied ...
                .replace("- { skill: dbt }", "- { skill: dbtt }")); // ... because the set is invalid
        int versionsBefore = versionCount("data-engineer");

        ConfigImportReport report = importService.importDirectory(config, false);

        assertThat(report.applied()).isFalse();
        assertThat(report.issues()).extracting(Object::toString)
                .containsExactly("role-profiles/data-engineer.yaml:skills.preferred[3].skill: unknown skill 'dbtt'");
        assertThat(versionCount("data-engineer")).isEqualTo(versionsBefore);
    }

    @Test
    void systemTemplatesHaveNoOwner() throws IOException {
        importService.importDirectory(copyOf(REPO_CONFIG), false);
        assertThat(jdbc.queryForObject(
                "select count(*) from role_profile where key in ('backend-engineer','quant-developer','data-engineer') and owner_user_id is null",
                Integer.class)).isEqualTo(3);
    }

    private int activeVersionNo(String key) {
        return jdbc.queryForObject("select v.version_no from role_profile p join role_profile_version v on v.id = p.active_version_id "
                + "where p.key = ? and p.owner_user_id is null", Integer.class, key);
    }

    private int versionCount(String key) {
        return jdbc.queryForObject("select count(*) from role_profile_version v join role_profile p on p.id = v.role_profile_id "
                + "where p.key = ? and p.owner_user_id is null", Integer.class, key);
    }

    private static List<String> keys(ConfigImportReport report, ConfigKind kind) {
        return report.outcomes().stream().filter(o -> o.kind() == kind).map(ConfigItemOutcome::key).toList();
    }

    private Path copyOf(Path source) throws IOException {
        Path target = Files.createTempDirectory(temp, "config");
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path destination = target.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(file, destination);
                }
            }
        }
        return target;
    }
}
