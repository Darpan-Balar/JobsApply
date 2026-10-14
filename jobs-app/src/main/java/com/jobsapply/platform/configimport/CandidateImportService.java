package com.jobsapply.platform.configimport;

import com.jobsapply.platform.candidate.CandidateImportOutcome;
import com.jobsapply.platform.candidate.CandidateProfileService;
import com.jobsapply.platform.candidate.CandidateSnapshots;
import com.jobsapply.platform.catalog.CatalogService;
import com.jobsapply.platform.catalog.CatalogSnapshot;
import com.jobsapply.platform.configimport.internal.ConfigLoader;
import com.jobsapply.platform.configimport.internal.LoadedConfig;
import com.jobsapply.platform.configimport.model.CandidateProfileDocument;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.spi.profile.CandidateSnapshot;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Imports a {@code CandidateProfile} YAML document into the current user's private profiles. Unless the caller trusts
 * the file, imported facts are marked UNVERIFIED and must be confirmed before matching or document generation may rely
 * on them. Reports reference paths and fact ids only — never personal values.
 */
@Service
@RequiredArgsConstructor
public class CandidateImportService {

    private final ConfigLoader loader;
    private final CatalogService catalog;
    private final CandidateProfileService candidates;
    private final TransactionTemplate transactions;

    public ConfigImportReport importProfile(String fileName, String yaml, boolean trustDeclaredVerification, boolean dryRun) {
        UserContext.requireUserId();
        LoadedConfig loaded = loader.loadText(fileName, yaml);
        if (!loaded.issues().isEmpty()) {
            return report(dryRun, loaded.issues());
        }
        if (loaded.documents().size() != 1 || loaded.documents().getFirst().kind() != ConfigKind.CANDIDATE_PROFILE) {
            return report(dryRun, List.of(new ConfigIssue(fileName, "kind", "expected kind CandidateProfile")));
        }
        CandidateProfileDocument document = loaded.documents().getFirst().contentAs(CandidateProfileDocument.class);
        CandidateSnapshot snapshot = document.toSnapshot();
        List<ConfigIssue> issues = new ArrayList<>();
        candidates.validate(snapshot).forEach(p -> issues.add(new ConfigIssue(fileName, "", p)));
        checkSkills(fileName, document, catalog.snapshot(), issues);
        if (!issues.isEmpty()) {
            return report(dryRun, issues);
        }
        CandidateSnapshot toStore = trustDeclaredVerification ? snapshot : CandidateSnapshots.asUnconfirmedImport(snapshot);
        CandidateImportOutcome outcome = transactions.execute(status -> {
            CandidateImportOutcome result = candidates.importProfile(document.key(), document.displayName(), toStore,
                    trustDeclaredVerification ? "imported (declared verification trusted)" : "imported (unconfirmed)");
            if (dryRun) {
                status.setRollbackOnly();
            }
            return result;
        });
        List<String> notices = trustDeclaredVerification ? List.of()
                : List.of("imported facts are UNVERIFIED until you confirm them (POST /api/v1/me/candidate-profiles/"
                        + document.key() + "/verification)");
        return new ConfigImportReport(dryRun, !dryRun, List.of(),
                List.of(new ConfigItemOutcome(ConfigKind.CANDIDATE_PROFILE, document.key(), outcome.name())), notices);
    }

    /** Skill references must be canonical catalog keys, so matching compares like with like. */
    private static void checkSkills(String file, CandidateProfileDocument doc, CatalogSnapshot catalog, List<ConfigIssue> issues) {
        for (int i = 0; i < doc.skills().size(); i++) {
            checkSkill(file, "skills[" + i + "].skill", doc.skills().get(i).skill(), catalog, issues);
        }
        for (int i = 0; i < doc.experiences().size(); i++) {
            List<String> skills = doc.experiences().get(i).skills();
            for (int j = 0; j < skills.size(); j++) {
                checkSkill(file, "experiences[" + i + "].skills[" + j + "]", skills.get(j), catalog, issues);
            }
        }
        for (int i = 0; i < doc.achievements().size(); i++) {
            List<String> skills = doc.achievements().get(i).skills();
            for (int j = 0; j < skills.size(); j++) {
                checkSkill(file, "achievements[" + i + "].skills[" + j + "]", skills.get(j), catalog, issues);
            }
        }
    }

    private static void checkSkill(String file, String path, String skill, CatalogSnapshot catalog, List<ConfigIssue> issues) {
        if (catalog.skillKeys().contains(skill)) {
            return;
        }
        issues.add(new ConfigIssue(file, path, catalog.resolveSkill(skill)
                .map(key -> "'" + skill + "' is an alias; use the canonical skill key '" + key + "'")
                .orElse("unknown skill '" + skill + "' (add it to the skill catalog first)")));
    }

    private static ConfigImportReport report(boolean dryRun, List<ConfigIssue> issues) {
        return new ConfigImportReport(dryRun, false, issues, List.of(), List.of());
    }
}
