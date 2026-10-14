package com.jobsapply.platform.preparation;

import com.jobsapply.platform.application.ApplicationService;
import com.jobsapply.platform.application.ApplicationStatus;
import com.jobsapply.platform.application.ApplicationView;
import com.jobsapply.platform.candidate.CandidateProfileService;
import com.jobsapply.platform.candidate.CandidateProfileService.CurrentVersion;
import com.jobsapply.platform.catalog.CatalogService;
import com.jobsapply.platform.discovery.AvailabilityService;
import com.jobsapply.platform.employer.EmployerService;
import com.jobsapply.platform.employer.SyncTarget;
import com.jobsapply.platform.matching.MatchView;
import com.jobsapply.platform.matching.MatchingService;
import com.jobsapply.platform.plugin.PluginRegistry;
import com.jobsapply.platform.posting.ListingIngestService;
import com.jobsapply.platform.posting.ListingRef;
import com.jobsapply.platform.posting.PostingQueryService;
import com.jobsapply.platform.posting.PostingViews;
import com.jobsapply.platform.preparation.PackageContent.Answer;
import com.jobsapply.platform.preparation.PackageContent.PackageIssue;
import com.jobsapply.platform.preparation.PackageContent.PackageIssue.Severity;
import com.jobsapply.platform.preparation.PreparationViews.Outcome;
import com.jobsapply.platform.preparation.PreparationViews.Result;
import com.jobsapply.platform.preparation.internal.ApplicationDocument;
import com.jobsapply.platform.preparation.internal.ApplicationDocumentRepository;
import com.jobsapply.platform.preparation.internal.ApplicationPackage;
import com.jobsapply.platform.preparation.internal.ApplicationPackageRepository;
import com.jobsapply.platform.roleprofile.RoleProfileService;
import com.jobsapply.platform.roleprofile.VisibleRoleProfile;
import com.jobsapply.platform.shared.crypto.PersonalDataCipher;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.shared.model.Actor;
import com.jobsapply.platform.shared.storage.DocumentStore;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.Capability;
import com.jobsapply.spi.common.Eligibility;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.matching.JobSnapshot;
import com.jobsapply.spi.matching.MatchContext;
import com.jobsapply.spi.profile.CandidateSnapshot;
import com.jobsapply.spi.profile.FactRef;
import com.jobsapply.spi.render.AnswerDraft;
import com.jobsapply.spi.render.AnswerDrafter;
import com.jobsapply.spi.render.ApplicationQuestion;
import com.jobsapply.spi.render.Claim;
import com.jobsapply.spi.render.CoverNoteGenerator;
import com.jobsapply.spi.render.DocumentRenderer;
import com.jobsapply.spi.render.RenderOptions;
import com.jobsapply.spi.render.RenderedDocument;
import com.jobsapply.spi.render.ResumeContentGenerator;
import com.jobsapply.spi.render.ResumeDocument;
import com.jobsapply.spi.render.ResumeTemplateDefinition;
import com.jobsapply.spi.source.JobSourceAdapter;
import com.jobsapply.spi.source.SourceBinding;
import com.jobsapply.spi.source.SourceJobRef;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prepares applications: recheck availability → evaluate the match → generate fact-backed documents and answer drafts
 * → validate traceability → store an immutable, encrypted package version → PREPARED, and READY_FOR_REVIEW only when
 * nothing blocks. Network calls happen before the single persisting transaction. Never submits anything.
 */
@Service
@RequiredArgsConstructor
public class PreparationService {

    public static final String DEFAULT_TEMPLATE = "one-page-technical";
    public static final String DEFAULT_DRAFTER = "rule-based-answers";
    private static final Set<ApplicationStatus> IN_FLIGHT_OR_DONE = EnumSet.of(
            ApplicationStatus.SUBMITTING, ApplicationStatus.SUBMITTED, ApplicationStatus.SUBMISSION_UNCONFIRMED);

    public record QuestionInput(String text, boolean required) {}

    private record Draft(String kind, String mediaType, String storageKey, long sizeBytes, String sha256) {}

    private final AvailabilityService availability;
    private final MatchingService matching;
    private final CandidateProfileService candidates;
    private final RoleProfileService roles;
    private final PostingQueryService postings;
    private final ListingIngestService listings;
    private final EmployerService employers;
    private final CatalogService catalog;
    private final ResumeTemplateService templates;
    private final ApplicationService applications;
    private final PluginRegistry<JobSourceAdapter> sourceAdapters;
    private final PluginRegistry<ResumeContentGenerator> generators;
    private final PluginRegistry<DocumentRenderer> renderers;
    private final PluginRegistry<CoverNoteGenerator> coverNotes;
    private final PluginRegistry<AnswerDrafter> drafters;
    private final TraceabilityValidator validator;
    private final ApplicationPackageRepository packages;
    private final ApplicationDocumentRepository documents;
    private final DocumentStore store;
    private final PersonalDataCipher cipher;
    private final JsonCodec json;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // --- prepare ---------------------------------------------------------------------------------------------------

    public Result prepare(UUID postingId, String candidateKey, String roleKey, List<QuestionInput> additionalQuestions) {
        UUID owner = UserContext.requireUserId();
        CurrentVersion candidate = candidates.current(candidateKey)
                .orElseThrow(() -> new NotFoundException("Candidate profile (with a version)", candidateKey));
        VisibleRoleProfile role = roles.findVisible(roleKey).orElseThrow(() -> new NotFoundException("Role profile", roleKey));

        AvailabilityService.AvailabilityReport recheck = availability.recheck(postingId);
        // Confirmed open only if a check made now saw it open; a stored OPEN with failed checks is not confirmation.
        Availability confirmed = recheck.availability() == Availability.CLOSED ? Availability.CLOSED
                : recheck.checks().stream().anyMatch(c -> c.availability() == Availability.OPEN) ? Availability.OPEN
                : Availability.UNKNOWN;
        if (confirmed == Availability.CLOSED) {
            Optional<ApplicationView> open = applications.findOpen(postingId, candidate.profileId());
            open.filter(a -> a.status().canTransitionTo(ApplicationStatus.EXPIRED)).ifPresent(a -> transactions.executeWithoutResult(s ->
                    applications.transition(a.id(), ApplicationStatus.EXPIRED, Actor.system(), "the source reports the posting closed")));
            return new Result(open.map(ApplicationView::id).orElse(null), ApplicationStatus.EXPIRED, Outcome.EXPIRED, null,
                    List.of(new PackageIssue(Severity.BLOCKING, "POSTING_CLOSED", "the posting is closed at its source")));
        }

        MatchView match = matching.evaluate(postingId, candidateKey, roleKey).match();
        if (match.eligibility() == Eligibility.NOT_ELIGIBLE) {
            ApplicationView app = transactions.execute(s -> {
                ApplicationView a = applications.openOrCreate(postingId, candidate.profileId(), candidate.versionId(), role.activeVersion().versionId());
                return a.status().canTransitionTo(ApplicationStatus.NOT_ELIGIBLE)
                        ? applications.transition(a.id(), ApplicationStatus.NOT_ELIGIBLE, Actor.system(), "a hard eligibility rule failed")
                        : a;
            });
            return new Result(app.id(), app.status(), Outcome.NOT_ELIGIBLE, null, match.result().ruleOutcomes().stream()
                    .filter(r -> r.verdict().name().equals("FAIL"))
                    .map(r -> new PackageIssue(Severity.BLOCKING, "NOT_ELIGIBLE", r.ruleType() + ": " + r.explanation())).toList());
        }

        PostingViews.Detail detail = postings.get(postingId);
        List<PackageIssue> issues = new ArrayList<>();
        Optional<PostingViews.Listing> destination = detail.listings().stream()
                .filter(l -> l.availability() == Availability.OPEN && l.applicationUrl() != null).findFirst();
        List<ApplicationQuestion> questions = new ArrayList<>(destination.map(d -> questions(postingId, d, issues)).orElse(List.of()));
        for (int i = 0; i < additionalQuestions.size(); i++) {
            QuestionInput q = additionalQuestions.get(i);
            questions.add(new ApplicationQuestion("custom-" + (i + 1), q.text(), ApplicationQuestion.QuestionType.TEXTAREA,
                    q.required(), List.of(), ApplicationQuestion.Kind.SCREENING));
        }

        Map<String, String> skillNames = catalog.skillDisplayNames();
        JobSnapshot base = postings.jobSnapshot(postingId);
        JobSnapshot job = base.toBuilder().skills(catalog.skillExtractor().extract(base.title(), base.descriptionText())).build();
        MatchContext context = new MatchContext(job, candidate.versionId(), candidate.snapshot(), role.definition(), clock.instant());

        ResumeTemplateSpec template = template(role, issues);
        ResumeDocument resume = generators.find(template.generator())
                .map(g -> g.generate(context, definition(template), skillNames))
                .orElseGet(() -> {
                    issues.add(blocking("GENERATOR_MISSING", "content generator '" + template.generator() + "' is not installed"));
                    return new ResumeDocument(template.key(), candidate.versionId(), null, List.of());
                });
        PackageContent.CoverNote coverNote = coverNote(context, role, detail, skillNames, issues);

        List<ApplicationQuestion> standard = questions.stream().filter(q -> q.kind() == ApplicationQuestion.Kind.STANDARD).toList();
        Optional<AnswerDrafter> drafter = drafters.find(DEFAULT_DRAFTER);
        List<Answer> answers = questions.stream().filter(q -> q.kind() != ApplicationQuestion.Kind.STANDARD)
                .map(q -> new Answer(q, drafter.map(d -> d.draft(q, context))
                        .orElseGet(() -> AnswerDraft.needsUser(q.id(), "no answer drafter installed"))))
                .toList();

        PackageContent content = new PackageContent(clock.instant(), job,
                destination.map(d -> new PackageContent.Destination(d.id(), d.sourceKey(), d.applicationUrl())).orElse(null),
                detail.summary().employerName(),
                new PackageContent.ProfileRef(candidateKey, candidate.versionId(), candidate.versionNo()),
                new PackageContent.ProfileRef(role.key(), role.activeVersion().versionId(), role.activeVersion().versionNo()),
                new PackageContent.MatchSummary(match.id(), match.score(), match.eligibility(),
                        match.result().uncertain().stream().map(u -> u.subject() + ": " + u.reason()).toList(), match.result().reasons()),
                template, resume, coverNote, standard, answers, List.of(),
                new PackageContent.AvailabilitySummary(confirmed,
                        recheck.checks().stream().map(c -> c.sourceKey() + " (" + c.method() + "): " + c.availability() + " — " + c.detail()).toList()),
                List.of());
        List<PackageIssue> all = new ArrayList<>(issues);
        all.addAll(validate(content, candidate.snapshot(), skillNames));
        if (confirmed != Availability.OPEN) {
            all.add(blocking("AVAILABILITY_UNCONFIRMED", "the posting could not be confirmed open at its source; recheck later"));
        }
        if (destination.isEmpty()) {
            all.add(blocking("NO_APPLICATION_URL", "no open source listing provides an application link"));
        }
        if (match.eligibility() == Eligibility.UNKNOWN) {
            all.add(new PackageIssue(Severity.WARNING, "ELIGIBILITY_UNKNOWN", "eligibility could not be established: "
                    + String.join("; ", content.match().uncertain())));
        }
        return store(owner, postingId, candidate, role, match.id(), withIssues(content, all), candidate.snapshot(), skillNames, null);
    }

    // --- edits -----------------------------------------------------------------------------------------------------

    /** The candidate answers (or clears) a question; creates a new package version. */
    public Result answer(UUID applicationId, String questionId, String text) {
        UUID owner = UserContext.requireUserId();
        Loaded loaded = load(applicationId);
        PackageContent content = loaded.content();
        if (content.answers().stream().noneMatch(a -> a.question().id().equals(questionId))) {
            throw new NotFoundException("Question", questionId);
        }
        List<Answer> answers = content.answers().stream().map(a -> !a.question().id().equals(questionId) ? a
                : new Answer(a.question(), text == null || text.isBlank()
                        ? AnswerDraft.needsUser(questionId, "cleared by you")
                        : new AnswerDraft(questionId, AnswerDraft.Status.DRAFTED, text.strip(), List.of(), AnswerDraft.Origin.USER, "your answer")))
                .toList();
        PackageContent edited = new PackageContent(clock.instant(), content.job(), content.destination(), content.employerName(),
                content.candidate(), content.role(), content.match(), content.template(), content.resume(), content.coverNote(),
                content.standardFields(), answers, content.documents(), content.availability(), List.of());
        return restore(owner, loaded, edited, false);
    }

    /** The candidate edits the resume: claims must cite facts of the package's candidate version, or be their approved text. */
    public Result editResume(UUID applicationId, List<PreparationViews.SectionEdit> sections) {
        UUID owner = UserContext.requireUserId();
        Loaded loaded = load(applicationId);
        PackageContent content = loaded.content();
        UUID version = content.candidate().versionId();
        ResumeDocument resume = new ResumeDocument(content.resume().templateKey(), version, content.resume().header(),
                sections.stream().map(s -> new ResumeDocument.Section(s.heading(), s.claims().stream()
                        .map(c -> new Claim(c.text(), c.factIds() == null ? List.of()
                                : c.factIds().stream().map(id -> new FactRef(version, id)).toList(), c.userApproved()))
                        .toList())).toList());
        PackageContent edited = new PackageContent(clock.instant(), content.job(), content.destination(), content.employerName(),
                content.candidate(), content.role(), content.match(), content.template(), resume, content.coverNote(),
                content.standardFields(), content.answers(), List.of(), content.availability(), List.of());
        CandidateSnapshot snapshot = candidates.findVersion(version).orElseThrow().snapshot();
        List<PackageIssue> claimIssues = validator.claims("resume", allClaims(resume), snapshot, version, catalog.skillDisplayNames());
        if (!claimIssues.isEmpty()) {
            throw new DomainRuleViolationException("The edited resume is not traceable to your facts: "
                    + String.join("; ", claimIssues.stream().map(PackageIssue::message).toList()));
        }
        return restore(owner, loaded, edited, true);
    }

    public ApplicationView reject(UUID applicationId, String reason) {
        UUID owner = UserContext.requireUserId();
        return transactions.execute(s -> applications.transition(applicationId, ApplicationStatus.REJECTED_BY_USER, Actor.user(owner),
                reason == null || reason.isBlank() ? "rejected by you" : reason));
    }

    // --- queries ---------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PreparationViews.ApplicationSummary> list() {
        List<ApplicationView> all = applications.list();
        Map<UUID, PostingViews.Summary> summaries = postings.summaries(all.stream().map(ApplicationView::postingId).distinct().toList());
        return all.stream().map(a -> {
            PostingViews.Summary p = summaries.get(a.postingId());
            Integer version = a.currentPackageId() == null ? null
                    : packages.findById(a.currentPackageId()).map(ApplicationPackage::getVersionNo).orElse(null);
            return new PreparationViews.ApplicationSummary(a.id(), a.status(), a.statusChangedAt(), a.postingId(),
                    p == null ? null : p.title(), p == null ? null : p.employerName(), version);
        }).toList();
    }

    @Transactional(readOnly = true)
    public PreparationViews.ApplicationDetail detail(UUID applicationId) {
        ApplicationView application = applications.get(applicationId);
        PostingViews.Summary posting = postings.summaries(List.of(application.postingId())).get(application.postingId());
        PreparationViews.PackageView current = application.currentPackageId() == null ? null
                : packages.findById(application.currentPackageId()).map(this::view).orElse(null);
        return new PreparationViews.ApplicationDetail(application, posting, current);
    }

    @Transactional(readOnly = true)
    public List<PreparationViews.PackageVersion> versions(UUID applicationId) {
        applications.get(applicationId);
        return packages.findByApplicationIdOrderByVersionNoDesc(applicationId).stream()
                .map(p -> new PreparationViews.PackageVersion(p.getId(), p.getVersionNo(), p.getCreatedAt(), p.getBlockingIssues(), p.getWarnings()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PreparationViews.Download document(UUID applicationId, UUID documentId) {
        UUID owner = UserContext.requireUserId();
        applications.get(applicationId);
        ApplicationDocument doc = documents.findById(documentId)
                .filter(d -> packages.findById(d.getPackageId()).map(p -> p.getApplicationId().equals(applicationId)).orElse(false))
                .orElseThrow(() -> new NotFoundException("Document", documentId));
        String extension = switch (doc.getMediaType()) {
            case "application/pdf" -> "pdf";
            case "text/html" -> "html";
            default -> "txt";
        };
        return new PreparationViews.Download(doc.getMediaType(), doc.getKind().toLowerCase() + "." + extension, store.get(owner, doc.getStorageKey()));
    }

    // --- internals -------------------------------------------------------------------------------------------------

    private record Loaded(ApplicationView application, ApplicationPackage current, PackageContent content) {}

    private Loaded load(UUID applicationId) {
        ApplicationView application = applications.get(applicationId);
        if (IN_FLIGHT_OR_DONE.contains(application.status())) {
            throw new DomainRuleViolationException("The application is " + application.status() + " and can no longer be edited");
        }
        if (application.currentPackageId() == null) {
            throw new DomainRuleViolationException("The application has no prepared package yet");
        }
        ApplicationPackage current = transactions.execute(s -> packages.findById(application.currentPackageId()).orElseThrow());
        return new Loaded(application, current, decrypt(current));
    }

    private Result restore(UUID owner, Loaded loaded, PackageContent edited, boolean rerender) {
        CandidateSnapshot snapshot = candidates.findVersion(edited.candidate().versionId()).orElseThrow().snapshot();
        Map<String, String> skillNames = catalog.skillDisplayNames();
        List<PackageIssue> issues = new ArrayList<>(loaded.content().issues().stream()
                .filter(i -> Set.of("AVAILABILITY_UNCONFIRMED", "NO_APPLICATION_URL", "ELIGIBILITY_UNKNOWN", "QUESTIONS_UNAVAILABLE",
                        "TEMPLATE_MISSING", "GENERATOR_MISSING", "COVER_NOTE_STRATEGY_MISSING").contains(i.code()))
                .toList());
        issues.addAll(validate(edited, snapshot, skillNames));
        PackageContent content = withIssues(edited, issues);
        List<Draft> reuse = rerender ? null : documents.findByPackageId(loaded.current().getId()).stream()
                .map(d -> new Draft(d.getKind(), d.getMediaType(), d.getStorageKey(), d.getSizeBytes(), d.getSha256())).toList();
        return store(owner, loaded.application().postingId(), null, null, loaded.application().jobMatchId(), content, snapshot, skillNames, reuse);
    }

    /**
     * Persists a new package version unless its substance equals the current one. {@code reuseDocuments} keeps the
     * current files (answer edits); {@code null} renders new files.
     */
    private Result store(UUID owner, UUID postingId, CurrentVersion candidate, VisibleRoleProfile role, UUID matchId,
            PackageContent content, CandidateSnapshot snapshot, Map<String, String> skillNames, List<Draft> reuseDocuments) {
        String fingerprint = cipher.fingerprint(json.write(stable(content)));
        UUID candidateProfileId = candidate != null ? candidate.profileId()
                : candidates.findVersion(content.candidate().versionId()).orElseThrow().ref().candidateProfileId();
        ApplicationView app = transactions.execute(s -> applications.openOrCreate(postingId, candidateProfileId,
                content.candidate().versionId(), content.role().versionId()));
        if (IN_FLIGHT_OR_DONE.contains(app.status())) {
            throw new DomainRuleViolationException("The application is " + app.status() + "; it cannot be prepared again");
        }
        if (app.currentPackageId() != null) {
            ApplicationPackage current = transactions.execute(s -> packages.findById(app.currentPackageId()).orElseThrow());
            if (current.getContentHash().equals(fingerprint)) {
                // After a definite failure the same package goes back to review; it then needs a fresh approval.
                ApplicationStatus status = app.status() != ApplicationStatus.FAILED ? app.status()
                        : transactions.execute(s -> advance(app.id(), current.getBlockingIssues() == 0));
                return new Result(app.id(), status, Outcome.UNCHANGED, current.getVersionNo(), content.issues());
            }
        }

        List<PackageIssue> issues = new ArrayList<>(content.issues());
        List<Draft> drafts = reuseDocuments != null ? reuseDocuments : render(owner, content, issues);
        PackageContent finalContent = withIssues(content, issues);
        int blocking = (int) finalContent.blockingIssues();
        int warnings = issues.size() - blocking;
        try {
            return transactions.execute(s -> {
                int versionNo = packages.findMaxVersionNo(app.id()).orElse(0) + 1;
                List<ApplicationDocument> docs = new ArrayList<>();
                List<PackageContent.DocumentRef> refs = new ArrayList<>();
                ApplicationPackage pkg = ApplicationPackage.create(app.id(), versionNo, id -> {
                    for (Draft d : drafts) {
                        ApplicationDocument doc = ApplicationDocument.builder().packageId(id).kind(d.kind()).mediaType(d.mediaType())
                                .sizeBytes(d.sizeBytes()).sha256(d.sha256()).storageKey(d.storageKey()).build();
                        docs.add(doc);
                        refs.add(new PackageContent.DocumentRef(doc.getId(), d.kind(), d.mediaType(), d.sizeBytes(), d.sha256()));
                    }
                    return cipher.encrypt(json.write(finalContent.withDocuments(refs)), aad(id, owner));
                }, fingerprint, blocking, warnings);
                packages.save(pkg);
                packages.flush();
                events.publishEvent(new PreparationEvents.PackageCreated(app.id(), owner, pkg.getId(), versionNo, blocking, warnings, clock.instant()));
                documents.saveAll(docs);
                applications.recordPreparation(app.id(), content.candidate().versionId(), content.role().versionId(),
                        matchId != null ? matchId : content.match().matchId(),
                        content.destination() == null ? null : content.destination().listingId(), pkg.getId());
                ApplicationStatus status = advance(app.id(), blocking == 0);
                return new Result(app.id(), status, versionNo == 1 ? Outcome.CREATED : Outcome.NEW_VERSION, versionNo, finalContent.issues());
            });
        } catch (RuntimeException e) {
            if (reuseDocuments == null) {
                drafts.forEach(d -> store.deleteQuietly(owner, d.storageKey()));
            }
            throw e;
        }
    }

    /** DISCOVERED/NOT_ELIGIBLE → MATCHED → PREPARED (re-review after any change) → READY_FOR_REVIEW when nothing blocks. */
    private ApplicationStatus advance(UUID applicationId, boolean nothingBlocks) {
        ApplicationStatus status = applications.get(applicationId).status();
        if (status == ApplicationStatus.DISCOVERED || status == ApplicationStatus.NOT_ELIGIBLE) {
            status = applications.transition(applicationId, ApplicationStatus.MATCHED, Actor.system(), "match evaluated").status();
        }
        if (status == ApplicationStatus.MATCHED || status == ApplicationStatus.FAILED || status == ApplicationStatus.READY_FOR_REVIEW
                || status == ApplicationStatus.APPROVED) {
            status = applications.transition(applicationId, ApplicationStatus.PREPARED, Actor.system(), "package prepared").status();
        }
        if (status == ApplicationStatus.PREPARED && nothingBlocks) {
            status = applications.transition(applicationId, ApplicationStatus.READY_FOR_REVIEW, Actor.system(), "ready for your review").status();
        }
        return status;
    }

    private List<Draft> render(UUID owner, PackageContent content, List<PackageIssue> issues) {
        List<Draft> drafts = new ArrayList<>();
        for (String rendererId : content.template().renderers()) {
            Optional<DocumentRenderer> renderer = renderers.find(rendererId);
            if (renderer.isEmpty()) {
                issues.add(blocking("RENDERER_MISSING", "renderer '" + rendererId + "' is not installed"));
                continue;
            }
            String mediaType = renderer.get().supportedMediaTypes().iterator().next();
            switch (renderer.get().render(content.resume(), new RenderOptions(mediaType, null))) {
                case PluginResult.Ok<RenderedDocument> ok -> drafts.add(put(owner, "RESUME", ok.value().mediaType(), ok.value().content()));
                case PluginResult.Failed<RenderedDocument> f -> issues.add(blocking("RENDER_FAILED", rendererId + ": " + f.message()));
                case PluginResult.Unsupported<RenderedDocument> u -> issues.add(blocking("RENDER_FAILED", rendererId + " is unsupported"));
            }
        }
        if (content.coverNote() != null) {
            drafts.add(put(owner, "COVER_NOTE", "text/plain", coverNoteText(content.coverNote()).getBytes(StandardCharsets.UTF_8)));
        }
        return drafts;
    }

    private Draft put(UUID owner, String kind, String mediaType, byte[] bytes) {
        return new Draft(kind, mediaType, store.put(owner, bytes), bytes.length, JsonCodec.sha256Hex(bytes));
    }

    private List<PackageIssue> validate(PackageContent content, CandidateSnapshot snapshot, Map<String, String> skillNames) {
        UUID version = content.candidate().versionId();
        List<PackageIssue> issues = new ArrayList<>(validator.claims("resume", allClaims(content.resume()), snapshot, version, skillNames));
        if (content.coverNote() != null) {
            issues.addAll(validator.claims("cover note", content.coverNote().claims(), snapshot, version, skillNames));
        }
        issues.addAll(validator.answers(content.answers(), snapshot, version));
        if (allClaims(content.resume()).isEmpty()) {
            issues.add(new PackageIssue(Severity.WARNING, "EMPTY_RESUME",
                    "the resume has no content: confirm your facts (only verified facts are used)"));
        }
        boolean outsideLatin1 = allClaims(content.resume()).stream().anyMatch(c -> c.text().chars().anyMatch(ch -> ch > 0xFF));
        if (outsideLatin1 && content.template().renderers().contains("pdf-resume")) {
            issues.add(new PackageIssue(Severity.WARNING, "PDF_FONT", "the PDF uses standard fonts; characters outside Latin-1 may not "
                    + "render — check the PDF, or use the HTML version"));
        }
        return issues;
    }

    private List<ApplicationQuestion> questions(UUID postingId, PostingViews.Listing destination, List<PackageIssue> issues) {
        Optional<ListingRef> listing = listings.listingsOf(postingId).stream().filter(l -> l.listingId().equals(destination.id())).findFirst();
        Optional<SyncTarget> target = listing.flatMap(l -> employers.syncTarget(l.employerSourceId()));
        Optional<JobSourceAdapter> adapter = target.flatMap(t -> sourceAdapters.find(t.adapterId()));
        if (adapter.isEmpty() || !adapter.get().descriptor().supports(Capability.APPLICATION_QUESTIONS)) {
            issues.add(new PackageIssue(Severity.WARNING, "QUESTIONS_UNAVAILABLE",
                    "this source does not publish its form questions; check the form yourself or add questions"));
            return List.of();
        }
        SyncTarget t = target.get();
        return switch (adapter.get().fetchApplicationQuestions(new SourceJobRef(
                new SourceBinding(t.sourceKey(), t.externalRef(), t.settings()), destination.externalId()))) {
            case PluginResult.Ok<List<ApplicationQuestion>> ok -> ok.value();
            case PluginResult.Failed<List<ApplicationQuestion>> f -> {
                issues.add(new PackageIssue(Severity.WARNING, "QUESTIONS_UNAVAILABLE", "could not fetch the form's questions: " + f.message()));
                yield List.of();
            }
            case PluginResult.Unsupported<List<ApplicationQuestion>> u -> List.of();
        };
    }

    private ResumeTemplateSpec template(VisibleRoleProfile role, List<PackageIssue> issues) {
        String key = role.definition().documents() != null && role.definition().documents().resumeTemplate() != null
                ? role.definition().documents().resumeTemplate() : DEFAULT_TEMPLATE;
        return templates.find(key).orElseGet(() -> {
            issues.add(new PackageIssue(Severity.WARNING, "TEMPLATE_MISSING", "resume template '" + key + "' is not configured; defaults used"));
            return ResumeTemplateSpec.builder().key(key).displayName(key).build();
        });
    }

    private PackageContent.CoverNote coverNote(MatchContext context, VisibleRoleProfile role, PostingViews.Detail detail,
            Map<String, String> skillNames, List<PackageIssue> issues) {
        var documentsPrefs = role.definition().documents();
        String strategy = documentsPrefs == null || documentsPrefs.coverNote() == null ? "none" : documentsPrefs.coverNote().strategy();
        if ("none".equals(strategy)) {
            return null;
        }
        Optional<CoverNoteGenerator> generator = coverNotes.find(strategy);
        if (generator.isEmpty()) {
            issues.add(new PackageIssue(Severity.WARNING, "COVER_NOTE_STRATEGY_MISSING", "cover-note strategy '" + strategy + "' is not installed"));
            return null;
        }
        String name = context.candidate().contact() == null ? null : context.candidate().contact().fullName();
        return new PackageContent.CoverNote(
                "Dear " + detail.summary().employerName() + " hiring team,\n\nI am applying for the " + context.job().title() + " position.",
                generator.get().claims(context, skillNames),
                "Kind regards," + (name == null ? "" : "\n" + name));
    }

    static String coverNoteText(PackageContent.CoverNote note) {
        StringBuilder text = new StringBuilder(note.greeting()).append("\n\n");
        note.claims().forEach(c -> text.append(c.text()).append('\n'));
        return text.append('\n').append(note.closing()).append('\n').toString();
    }

    private PreparationViews.PackageView view(ApplicationPackage p) {
        return new PreparationViews.PackageView(p.getId(), p.getVersionNo(), p.getCreatedAt(), p.getBlockingIssues(), p.getWarnings(),
                p.getContentHash(), decrypt(p));
    }

    private PackageContent decrypt(ApplicationPackage p) {
        return json.read(cipher.decrypt(p.getPayloadJson(), aad(p.getId(), p.getOwnerUserId())), PackageContent.class);
    }

    /** Substance only: no timestamps, file references or volatile availability evidence. */
    private static PackageContent stable(PackageContent c) {
        return new PackageContent(null, c.job(), c.destination(), c.employerName(), c.candidate(), c.role(), c.match(), c.template(),
                c.resume(), c.coverNote(), c.standardFields(), c.answers(), List.of(),
                new PackageContent.AvailabilitySummary(c.availability().availability(), List.of()), c.issues());
    }

    private static PackageContent withIssues(PackageContent c, List<PackageIssue> issues) {
        return new PackageContent(c.generatedAt(), c.job(), c.destination(), c.employerName(), c.candidate(), c.role(), c.match(),
                c.template(), c.resume(), c.coverNote(), c.standardFields(), c.answers(), c.documents(), c.availability(), issues);
    }

    static List<Claim> allClaims(ResumeDocument resume) {
        return resume.sections().stream().flatMap(s -> s.claims().stream()).toList();
    }

    private static ResumeTemplateDefinition definition(ResumeTemplateSpec t) {
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("maxExperiences", t.maxExperiences());
        layout.put("maxAchievementsPerExperience", t.maxAchievementsPerExperience());
        layout.put("maxSkills", t.maxSkills());
        layout.put("includeEducation", t.includeEducation());
        return new ResumeTemplateDefinition(t.key(), t.renderers().getFirst(), layout);
    }

    private static String aad(UUID packageId, UUID owner) {
        return "application_package|" + packageId + "|" + owner;
    }

    private static PackageIssue blocking(String code, String message) {
        return new PackageIssue(Severity.BLOCKING, code, message);
    }
}
