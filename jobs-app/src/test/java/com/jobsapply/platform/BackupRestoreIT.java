package com.jobsapply.platform;

import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.configimport.CandidateImportService;
import com.jobsapply.platform.configimport.ConfigImportService;
import com.jobsapply.platform.candidate.MasterResumeService;
import com.jobsapply.platform.identity.NewUser;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.platform.support.CandidateYaml;
import com.jobsapply.platform.support.IntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The backup scripts against a real PostgreSQL: a backup restores completely (row counts and documents identical),
 * damage is detected, and a restore never overwrites existing data (ADR-0014).
 */
@IntegrationTest
class BackupRestoreIT {

    static final Path SCRIPTS = Path.of("..", "scripts");

    @Autowired PostgreSQLContainer postgres;
    @Autowired UserService users;
    @Autowired ConfigImportService config;
    @Autowired CandidateImportService candidates;
    @Autowired MasterResumeService resumes;
    @Value("${jobs.storage.directory}") Path storage;

    record Result(int exit, String output) {}

    private Result run(String script, String... args) throws IOException, InterruptedException {
        List<String> command = Stream.concat(Stream.of("bash", SCRIPTS.resolve(script).toString()), Stream.of(args)).toList();
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().putAll(Map.of("JOBS_DB_CONTAINER", postgres.getContainerId(), "JOBS_DB_NAME", postgres.getDatabaseName(),
                "JOBS_DB_USER", postgres.getUsername(), "JOBS_STORAGE_DIR", storage.toAbsolutePath().toString()));
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.waitFor(), output);
    }

    private String query(String database, String sql) throws IOException, InterruptedException {
        return postgres.execInContainer("psql", "-U", postgres.getUsername(), "-d", database, "-AtX", "-c", sql).getStdout().strip();
    }

    private Path latest(Path root) throws IOException {
        try (Stream<Path> dirs = Files.list(root)) {
            return dirs.filter(Files::isDirectory).max(Path::compareTo).orElseThrow();
        }
    }

    @Test
    void aBackupRestoresCompletelyAndDamageIsDetected() throws Exception {
        assertThat(config.importDirectory(Path.of("..", "config"), false).issues()).isEmpty();
        UUID user = users.createUser(NewUser.builder().username(uniqueKey("bk")).displayName("B").build()).id();
        String key = uniqueKey("main");
        byte[] resume = "%PDF-1.7\nbackup test resume".getBytes(StandardCharsets.US_ASCII);
        UserContext.runAs(user, () -> {
            candidates.importProfile("me.yaml", CandidateYaml.profile(key), true, false);
            resumes.upload(key, "cv.pdf", "application/pdf", resume);
        });
        Path out = Files.createTempDirectory(Path.of("target"), "backups-");

        Result backup = run("backup.sh", out.toString());
        assertThat(backup.exit()).as(backup.output()).isZero();
        Path dir = latest(out);
        String manifest = Files.readString(dir.resolve("manifest.properties"));
        assertThat(manifest).contains("schema_version=14", "rows.candidate_profile=", "rows.app_user=");
        assertThat(manifest).as("the manifest holds counts only").doesNotContain(key);

        Result verify = run("verify-backup.sh", dir.toString());
        assertThat(verify.exit()).as(verify.output()).isZero();
        assertThat(verify.output()).contains("counts match");

        String database = "restored_" + UUID.randomUUID().toString().substring(0, 8);
        Path documents = out.resolve("documents-" + database);
        Result restore = run("restore.sh", dir.toString(), database, documents.toString());
        assertThat(restore.exit()).as(restore.output()).isZero();
        assertThat(query(database, "select count(*) from app_user where id = '" + user + "'")).isEqualTo("1");
        assertThat(query(database, "select count(*) from master_resume")).isEqualTo(query(postgres.getDatabaseName(),
                "select count(*) from master_resume"));
        try (Stream<Path> restored = Files.walk(documents)) {
            assertThat(restored.filter(Files::isRegularFile).count()).isPositive();
        }

        Result again = run("restore.sh", dir.toString(), database, out.resolve("other").toString());
        assertThat(again.exit()).as("an existing database is never overwritten").isNotZero();
        assertThat(again.output()).contains("already exists");

        byte[] dump = Files.readAllBytes(dir.resolve("database.dump"));
        dump[dump.length / 2] ^= 0x5A;
        Files.write(dir.resolve("database.dump"), dump);
        Result damaged = run("verify-backup.sh", dir.toString());
        assertThat(damaged.exit()).isNotZero();
        assertThat(damaged.output()).contains("damaged");

        query("postgres", "drop database \"" + database + "\"");
    }
}
