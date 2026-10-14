package com.jobsapply.platform.configimport.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.configimport.ConfigIssue;
import com.jobsapply.platform.configimport.model.EmployerSetConfig.EmployerConfig;
import com.jobsapply.platform.configimport.model.EmployerSetConfig.EmployerSourceConfig;
import com.jobsapply.platform.employer.BindingView;
import com.jobsapply.platform.employer.EmployerView;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmployerCsvTest {

    @Test
    void parsesQuotedCellsListsAndBindings() {
        String csv = EmployerCsv.HEADER + "\n"
                + "acme,\"Acme, Inc.\",https://acme.example,,fintech,fintech; payments,\"Paris, FR\",80,true,greenhouse-public:acme;lever-public:acme-co:90\n"
                + "\n"
                + "beta,Beta,,,,,,,,\n";

        EmployerCsv.Parsed parsed = EmployerCsv.parse("e.csv", csv);

        assertThat(parsed.issues()).isEmpty();
        List<EmployerConfig> employers = parsed.content().employers();
        assertThat(employers).hasSize(2);
        EmployerConfig acme = employers.getFirst();
        assertThat(acme.displayName()).isEqualTo("Acme, Inc.");
        assertThat(acme.careersUrl()).isNull();
        assertThat(acme.tags()).containsExactly("fintech", "payments");
        assertThat(acme.headquarters()).isEqualTo("Paris, FR");
        assertThat(acme.discoveryPriority()).isEqualTo(80);
        assertThat(acme.metadata()).as("CSV cannot express metadata: left unchanged").isNull();
        assertThat(acme.sources()).containsExactly(
                new EmployerSourceConfig("greenhouse-public", "acme", true, 50),
                new EmployerSourceConfig("lever-public", "acme-co", true, 90));
        EmployerConfig beta = employers.get(1);
        assertThat(beta.enabled()).isTrue();
        assertThat(beta.discoveryPriority()).isEqualTo(50);
        assertThat(beta.sources()).isEmpty();
    }

    @Test
    void columnsMayBeReorderedAndOmittedExceptRequiredOnes() {
        EmployerCsv.Parsed parsed = EmployerCsv.parse("e.csv", "displayName,key\nAcme,acme\n");
        assertThat(parsed.issues()).isEmpty();
        assertThat(parsed.content().employers().getFirst().key()).isEqualTo("acme");
    }

    @Test
    void reportsHeaderProblems() {
        assertThat(EmployerCsv.parse("e.csv", "key,name\nacme,Acme\n").issues()).extracting(ConfigIssue::toString).containsExactly(
                "e.csv:header: unknown column 'name'; expected: " + EmployerCsv.HEADER,
                "e.csv:header: missing required column 'displayName'");
        assertThat(EmployerCsv.parse("e.csv", "").issues()).extracting(ConfigIssue::message)
                .singleElement().asString().startsWith("empty file");
    }

    @Test
    void reportsCellProblemsByRow() {
        String csv = "key,displayName,discoveryPriority,enabled,sources\n"
                + "acme,Acme,high,maybe,greenhouse-public\n";
        assertThat(EmployerCsv.parse("e.csv", csv).issues()).extracting(ConfigIssue::toString).containsExactly(
                "e.csv:row 2.discoveryPriority: not a whole number: 'high'",
                "e.csv:row 2.enabled: expected true or false, got 'maybe'",
                "e.csv:row 2.sources[0]: expected sourceKey:externalRef[:priority], got 'greenhouse-public'");
    }

    @Test
    void rewritesValidationPathsToRows() {
        assertThat(EmployerCsv.toRowPath(new ConfigIssue("e.csv", "employers[0].website", "bad")).path()).isEqualTo("row 2.website");
        assertThat(EmployerCsv.toRowPath(new ConfigIssue("e.csv", "employers[3]", "bad")).path()).isEqualTo("row 5");
    }

    @Test
    void writesQuotedCellsAndNeutralizesFormulas() {
        EmployerView view = new EmployerView(UUID.randomUUID(), "acme", "=HYPERLINK(\"x\")", "acme", null, null, "fin,tech",
                List.of("a", "b"), "@home", 80, true, null, Map.of(),
                List.of(new BindingView("greenhouse-public", "gh", "acme", true, 50, null, null, 0, null),
                        new BindingView("lever-public", "lv", "acme", true, 70, null, null, 0, null),
                        new BindingView("ashby-public", "as", "old", false, 50, null, null, 0, null)),
                3);

        String[] lines = EmployerCsv.write(List.of(view)).split("\r\n");

        assertThat(lines[0]).isEqualTo(EmployerCsv.HEADER);
        assertThat(lines[1]).isEqualTo("acme,\"'=HYPERLINK(\"\"x\"\")\",,,\"fin,tech\",a;b,'@home,80,true,greenhouse-public:acme;lever-public:acme:70");
    }

    @Test
    void writtenCsvParsesBackToTheSameContent() {
        EmployerView view = new EmployerView(UUID.randomUUID(), "acme", "Acme, \"The\" Company", "acme", "https://acme.example",
                null, "fintech", List.of("fintech"), null, 65, false, null, Map.of(),
                List.of(new BindingView("greenhouse-public", "gh", "acme", true, 75, null, null, 0, null)), 1);

        EmployerConfig parsed = EmployerCsv.parse("e.csv", EmployerCsv.write(List.of(view))).content().employers().getFirst();

        assertThat(parsed.displayName()).isEqualTo("Acme, \"The\" Company");
        assertThat(parsed.website()).isEqualTo("https://acme.example");
        assertThat(parsed.enabled()).isFalse();
        assertThat(parsed.discoveryPriority()).isEqualTo(65);
        assertThat(parsed.sources()).containsExactly(new EmployerSourceConfig("greenhouse-public", "acme", true, 75));
    }
}
