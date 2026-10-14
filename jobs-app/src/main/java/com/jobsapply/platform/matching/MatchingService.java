package com.jobsapply.platform.matching;

import com.jobsapply.platform.candidate.CandidateProfileService;
import com.jobsapply.platform.candidate.CandidateProfileService.CurrentVersion;
import com.jobsapply.platform.catalog.CatalogService;
import com.jobsapply.platform.catalog.SkillExtractor;
import com.jobsapply.platform.employer.EmployerPreferenceService;
import com.jobsapply.platform.matching.internal.JobMatch;
import com.jobsapply.platform.matching.internal.JobMatchRepository;
import com.jobsapply.platform.plugin.PluginRegistry;
import com.jobsapply.platform.posting.PostingQueryService;
import com.jobsapply.platform.posting.PostingViews;
import com.jobsapply.platform.roleprofile.RoleProfileService;
import com.jobsapply.platform.roleprofile.VisibleRoleProfile;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.shared.model.PageResult;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.Eligibility;
import com.jobsapply.spi.matching.JobSnapshot;
import com.jobsapply.spi.matching.MatchContext;
import com.jobsapply.spi.matching.MatchResult;
import com.jobsapply.spi.matching.MatchingStrategy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Public API of matching. Results are keyed by (posting, candidate version, role version, strategy, strategy version):
 * re-evaluating updates that row and reports whether the result changed. All data is the current user's own.
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    public enum Outcome { CREATED, UPDATED, UNCHANGED }

    public record Evaluation(Outcome outcome, MatchView match) {}

    private final CandidateProfileService candidates;
    private final RoleProfileService roleProfiles;
    private final PostingQueryService postings;
    private final CatalogService catalog;
    private final EmployerPreferenceService preferences;
    private final PluginRegistry<MatchingStrategy> strategies;
    private final JobMatchRepository matches;
    private final JsonCodec json;
    private final TransactionTemplate transactions;
    private final Clock clock;

    private record Inputs(String candidateKey, CurrentVersion candidate, VisibleRoleProfile role, MatchingStrategy strategy, SkillExtractor skills) {}

    public Evaluation evaluate(UUID postingId, String candidateProfileKey, String roleProfileKey) {
        Inputs inputs = inputs(candidateProfileKey, roleProfileKey);
        return transactions.execute(status -> evaluate(postingId, inputs));
    }

    /** Evaluates every open posting except those of employers the user ignores; each in its own short transaction. */
    public MatchRunReport run(String candidateProfileKey, String roleProfileKey) {
        Inputs inputs = inputs(candidateProfileKey, roleProfileKey);
        Set<UUID> ignored = preferences.ignoredEmployerIds();
        List<UUID> all = postings.postingIds(Availability.OPEN, Set.of());
        List<UUID> targets = postings.postingIds(Availability.OPEN, ignored);
        int created = 0, updated = 0, unchanged = 0, eligible = 0, notEligible = 0, unknown = 0, shortlisted = 0;
        for (UUID postingId : targets) {
            Evaluation evaluation = transactions.execute(status -> evaluate(postingId, inputs));
            switch (evaluation.outcome()) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
            }
            switch (evaluation.match().eligibility()) {
                case ELIGIBLE -> eligible++;
                case NOT_ELIGIBLE -> notEligible++;
                case UNKNOWN -> unknown++;
            }
            if (evaluation.match().shortlisted()) {
                shortlisted++;
            }
        }
        return new MatchRunReport(label(inputs), roleProfileKey, targets.size(), created, updated, unchanged,
                eligible, notEligible, unknown, shortlisted, all.size() - targets.size());
    }

    /** Matches of the profiles' current versions, best first. */
    @Transactional(readOnly = true)
    public PageResult<MatchView> list(String candidateProfileKey, String roleProfileKey, Eligibility eligibility,
            BigDecimal minScore, int page, int size) {
        CurrentVersion candidate = candidate(candidateProfileKey);
        VisibleRoleProfile role = role(roleProfileKey);
        Page<JobMatch> found = matches.search(candidate.versionId(), role.activeVersion().versionId(), eligibility, minScore,
                PageRequest.of(page, size));
        Map<UUID, PostingViews.Summary> summaries = postings.summaries(found.getContent().stream().map(JobMatch::getJobPostingId).toList());
        String candidateLabel = candidateProfileKey + " v" + candidate.versionNo();
        return PageResult.of(found, m -> view(m, summaries.get(m.getJobPostingId()), candidateLabel, role));
    }

    @Transactional(readOnly = true)
    public MatchView get(UUID matchId) {
        UserContext.requireUserId();
        JobMatch match = matches.findById(matchId).orElseThrow(() -> new NotFoundException("Match", matchId));
        String candidateLabel = candidates.versionLabels(Set.of(match.getCandidateProfileVersionId()))
                .getOrDefault(match.getCandidateProfileVersionId(), "?");
        VisibleRoleProfile role = roleProfiles.listVisible().stream()
                .filter(r -> r.activeVersion().versionId().equals(match.getRoleProfileVersionId())).findFirst().orElse(null);
        return view(match, postings.summaries(List.of(match.getJobPostingId())).get(match.getJobPostingId()), candidateLabel, role);
    }

    private Evaluation evaluate(UUID postingId, Inputs inputs) {
        JobSnapshot base = postings.jobSnapshot(postingId);
        JobSnapshot job = base.toBuilder().skills(inputs.skills().extract(base.title(), base.descriptionText())).build();
        Instant now = clock.instant();
        MatchResult result = inputs.strategy().evaluate(
                new MatchContext(job, inputs.candidate().versionId(), inputs.candidate().snapshot(), inputs.role().definition(), now));
        String resultJson = json.write(result);

        Optional<JobMatch> existing = matches.findByJobPostingIdAndCandidateProfileVersionIdAndRoleProfileVersionIdAndStrategyKeyAndStrategyVersion(
                postingId, inputs.candidate().versionId(), inputs.role().activeVersion().versionId(), result.strategyKey(), result.strategyVersion());
        Outcome outcome;
        JobMatch match;
        if (existing.isEmpty()) {
            match = matches.save(JobMatch.builder()
                    .jobPostingId(postingId)
                    .candidateProfileVersionId(inputs.candidate().versionId())
                    .roleProfileVersionId(inputs.role().activeVersion().versionId())
                    .strategyKey(result.strategyKey())
                    .strategyVersion(result.strategyVersion())
                    .score(result.score())
                    .eligibility(result.eligibility())
                    .resultJson(resultJson)
                    .evaluatedAt(now)
                    .build());
            outcome = Outcome.CREATED;
        } else {
            match = existing.get();
            if (json.canonicalize(match.getResultJson()).equals(resultJson)) {
                outcome = Outcome.UNCHANGED; // stored jsonb is compared by content
            } else {
                match.replaceResult(result.score(), result.eligibility(), resultJson, now);
                outcome = Outcome.UPDATED;
            }
        }
        matches.flush();
        PostingViews.Summary summary = postings.summaries(List.of(postingId)).get(postingId);
        return new Evaluation(outcome, view(match, summary, label(inputs), inputs.role()));
    }

    private Inputs inputs(String candidateProfileKey, String roleProfileKey) {
        UserContext.requireUserId();
        CurrentVersion candidate = candidate(candidateProfileKey);
        VisibleRoleProfile role = role(roleProfileKey);
        String strategyKey = role.definition().scoring().strategy();
        MatchingStrategy strategy = strategies.find(strategyKey)
                .orElseThrow(() -> new DomainRuleViolationException("Matching strategy '" + strategyKey + "' is not installed"));
        if (strategy.strategyVersion() != role.definition().scoring().strategyVersion()) {
            throw new DomainRuleViolationException("Role profile '" + roleProfileKey + "' asks for " + strategyKey + " v"
                    + role.definition().scoring().strategyVersion() + " but v" + strategy.strategyVersion() + " is installed");
        }
        return new Inputs(candidateProfileKey, candidate, role, strategy, catalog.skillExtractor());
    }

    private CurrentVersion candidate(String key) {
        return candidates.current(key).orElseThrow(() -> new NotFoundException("Candidate profile (with a version)", key));
    }

    private VisibleRoleProfile role(String key) {
        return roleProfiles.findVisible(key).orElseThrow(() -> new NotFoundException("Role profile", key));
    }

    private static String label(Inputs inputs) {
        return inputs.candidateKey() + " v" + inputs.candidate().versionNo();
    }

    private MatchView view(JobMatch m, PostingViews.Summary posting, String candidateLabel, VisibleRoleProfile role) {
        MatchResult result = json.read(m.getResultJson(), MatchResult.class);
        boolean shortlisted = role != null && m.getScore() != null && m.getEligibility() != Eligibility.NOT_ELIGIBLE
                && m.getScore().compareTo(role.definition().scoring().thresholds().shortlist()) >= 0;
        return new MatchView(m.getId(), m.getJobPostingId(),
                posting == null ? null : posting.employerKey(), posting == null ? null : posting.employerName(),
                posting == null ? null : posting.title(), posting == null ? null : posting.availability(),
                candidateLabel, role == null ? null : role.key(), role == null ? 0 : role.activeVersion().versionNo(),
                m.getStrategyKey() + " v" + m.getStrategyVersion(), m.getScore(), m.getEligibility(), shortlisted, m.getEvaluatedAt(), result);
    }
}
