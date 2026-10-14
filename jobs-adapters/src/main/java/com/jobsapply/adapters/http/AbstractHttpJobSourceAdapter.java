package com.jobsapply.adapters.http;

import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.Capability;
import com.jobsapply.spi.common.FailureKind;
import com.jobsapply.spi.common.HealthReport;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.ValidationReport;
import com.jobsapply.spi.source.AvailabilityObservation;
import com.jobsapply.spi.source.DiscoveryPage;
import com.jobsapply.spi.source.DiscoveryRequest;
import com.jobsapply.spi.source.JobSourceAdapter;
import com.jobsapply.spi.source.SourceBinding;
import com.jobsapply.spi.source.SourceJob;
import com.jobsapply.spi.source.SourceJobRef;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Template Method base for JSON-over-HTTPS job sources. Subclasses provide URLs and parsing; this class binds settings,
 * validates the binding, performs resilient fetching, maps failures, and supplies the generic availability rule:
 * a job-level 404 counts as authoritative closure only if the board itself still responds.
 *
 * @param <S> the adapter's settings record
 */
public abstract class AbstractHttpJobSourceAdapter<S extends HttpSourceSettings> implements JobSourceAdapter {

    /** Board tokens / site names are path segments: restrict them so they cannot alter the URL. */
    private static final Pattern EXTERNAL_REF = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,199}$");

    protected final HttpFetcher fetcher;
    protected final Clock clock;
    private final Class<S> settingsType;
    private final JsonMapper mapper = JsonMapper.builder().build();

    protected AbstractHttpJobSourceAdapter(HttpFetcher fetcher, Clock clock, Class<S> settingsType) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.settingsType = settingsType;
    }

    // --- hooks -------------------------------------------------------------------------------------------------

    protected abstract URI listUri(SourceBinding binding, S settings, String cursor, int pageSize);

    /** Parses one page; throw {@link ParseProblem} if the document as a whole is unusable. */
    protected abstract DiscoveryPage parsePage(JsonNode body, DiscoveryRequest request, S settings);

    /** Cheapest request proving the board exists (used to qualify a job-level 404). Defaults to the list URI. */
    protected URI boardProbeUri(SourceBinding binding, S settings) {
        return listUri(binding, settings, null, 1);
    }

    /** URI of a single job, or {@code null} if the provider has no such endpoint. */
    protected URI jobUri(SourceBinding binding, S settings, String externalId) {
        return null;
    }

    protected SourceJob parseJob(JsonNode body, SourceJobRef ref, S settings) {
        throw new UnsupportedOperationException();
    }

    // --- template ----------------------------------------------------------------------------------------------

    @Override
    public ValidationReport validateConfiguration(SourceBinding binding) {
        if (binding.externalRef() == null || !EXTERNAL_REF.matcher(binding.externalRef()).matches()) {
            return new ValidationReport(List.of(new ValidationReport.Issue("externalRef",
                    "must be a board identifier of letters, digits, '.', '_' or '-'")));
        }
        try {
            settings(binding);
            return ValidationReport.valid();
        } catch (IllegalArgumentException | JacksonException e) {
            return new ValidationReport(List.of(new ValidationReport.Issue("settings", rootMessage(e))));
        }
    }

    @Override
    public final PluginResult<DiscoveryPage> discoverJobs(DiscoveryRequest request) {
        return withSettings(request.binding(), settings -> {
            PluginResult<JsonNode> body = fetcher.getJson(listUri(request.binding(), settings, request.cursor(), request.pageSize()), settings);
            return switch (body) {
                case PluginResult.Ok<JsonNode> ok -> parse(() -> parsePage(ok.value(), request, settings));
                case PluginResult.Failed<JsonNode> failed -> new PluginResult.Failed<>(failed.kind(), failed.message(), failed.retryAfter());
                case PluginResult.Unsupported<JsonNode> unsupported -> PluginResult.unsupported(unsupported.capability());
            };
        });
    }

    @Override
    public PluginResult<SourceJob> fetchJobDetails(SourceJobRef ref) {
        if (!descriptor().supports(Capability.JOB_DETAILS)) {
            return PluginResult.unsupported(Capability.JOB_DETAILS);
        }
        return withSettings(ref.binding(), settings -> {
            PluginResult<JsonNode> body = fetcher.getJson(jobUri(ref.binding(), settings, ref.externalId()), settings);
            return switch (body) {
                case PluginResult.Ok<JsonNode> ok -> parse(() -> parseJob(ok.value(), ref, settings));
                case PluginResult.Failed<JsonNode> failed -> new PluginResult.Failed<>(failed.kind(), failed.message(), failed.retryAfter());
                case PluginResult.Unsupported<JsonNode> unsupported -> PluginResult.unsupported(unsupported.capability());
            };
        });
    }

    /**
     * 200 on the job → OPEN (authoritative). 404 on the job → CLOSED (authoritative) only if the board's listing
     * still answers 200; otherwise the board itself is missing or failing, which says nothing about the job.
     */
    @Override
    public PluginResult<AvailabilityObservation> checkAvailability(SourceJobRef ref) {
        if (!descriptor().supports(Capability.AVAILABILITY_CHECK)) {
            return PluginResult.unsupported(Capability.AVAILABILITY_CHECK);
        }
        return withSettings(ref.binding(), settings -> {
            URI jobUri = jobUri(ref.binding(), settings, ref.externalId());
            PluginResult<JsonNode> job = fetcher.getJson(jobUri, settings);
            if (job instanceof PluginResult.Ok<JsonNode>) {
                return PluginResult.ok(new AvailabilityObservation(
                        Availability.OPEN, true, "HTTP 200 from " + jobUri, clock.instant()));
            }
            PluginResult.Failed<JsonNode> failed = (PluginResult.Failed<JsonNode>) job;
            if (failed.kind() != FailureKind.NOT_FOUND) {
                return new PluginResult.Failed<>(failed.kind(), failed.message(), failed.retryAfter());
            }
            URI boardUri = boardProbeUri(ref.binding(), settings);
            PluginResult<JsonNode> board = fetcher.getJson(boardUri, settings);
            if (board instanceof PluginResult.Failed<JsonNode> boardFailed) {
                return new PluginResult.Failed<>(boardFailed.kind(), "job returned 404 but the board check failed: " + boardFailed.message(), boardFailed.retryAfter());
            }
            return PluginResult.ok(new AvailabilityObservation(Availability.CLOSED, true,
                    "HTTP 404 from " + jobUri + " while the board responded 200", clock.instant()));
        });
    }

    @Override
    public HealthReport healthCheck(SourceBinding binding) {
        PluginResult<DiscoveryPage> page = discoverJobs(new DiscoveryRequest(binding, null, 1, null));
        return switch (page) {
            case PluginResult.Ok<DiscoveryPage> ok -> new HealthReport(HealthReport.Status.UP, "listing reachable", clock.instant());
            case PluginResult.Failed<DiscoveryPage> failed -> new HealthReport(
                    failed.kind() == FailureKind.TRANSIENT || failed.kind() == FailureKind.RATE_LIMITED
                            ? HealthReport.Status.DEGRADED : HealthReport.Status.DOWN,
                    failed.message(), clock.instant());
            case PluginResult.Unsupported<DiscoveryPage> unsupported -> new HealthReport(HealthReport.Status.UNKNOWN, "unsupported", clock.instant());
        };
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    protected S settings(SourceBinding binding) {
        return mapper.convertValue(binding.settings(), settingsType);
    }

    private <T> PluginResult<T> withSettings(SourceBinding binding, Function<S, PluginResult<T>> action) {
        ValidationReport report = validateConfiguration(binding);
        if (!report.isValid()) {
            ValidationReport.Issue issue = report.issues().getFirst();
            return PluginResult.failed(FailureKind.PERMANENT, "invalid binding: " + issue.path() + " " + issue.message());
        }
        try {
            return action.apply(settings(binding));
        } catch (IllegalArgumentException e) {
            // e.g. a malformed job id or cursor: never let it escape the adapter
            return PluginResult.failed(FailureKind.PERMANENT, "invalid request: " + e.getMessage());
        }
    }

    private static <T> PluginResult<T> parse(Supplier<T> parser) {
        try {
            return PluginResult.ok(parser.get());
        } catch (ParseProblem | IllegalArgumentException | NullPointerException e) {
            return PluginResult.failed(FailureKind.PARSE, "unexpected response shape: " + e.getMessage());
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? e.getMessage() : root.getMessage();
        return message == null ? "invalid" : message.lines().findFirst().orElse("invalid");
    }

    /** The provider's document is not in the documented shape. */
    public static final class ParseProblem extends RuntimeException {
        public ParseProblem(String message) {
            super(message);
        }
    }
}
