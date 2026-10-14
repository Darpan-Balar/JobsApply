package com.jobsapply.adapters.source.greenhouse;

import static com.jobsapply.adapters.http.Json.array;
import static com.jobsapply.adapters.http.Json.decimal;
import static com.jobsapply.adapters.http.Json.instant;
import static com.jobsapply.adapters.http.Json.object;
import static com.jobsapply.adapters.http.Json.text;

import com.jobsapply.adapters.http.AbstractHttpJobSourceAdapter;
import com.jobsapply.adapters.http.HttpFetcher;
import com.jobsapply.spi.common.Capability;
import com.jobsapply.spi.common.ComplianceNote;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.source.Compensation;
import com.jobsapply.spi.source.DiscoveryPage;
import com.jobsapply.spi.source.DiscoveryRequest;
import com.jobsapply.spi.source.RawLocation;
import com.jobsapply.spi.source.SourceBinding;
import com.jobsapply.spi.source.SourceJob;
import com.jobsapply.spi.source.SourceJobRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jsoup.parser.Parser;
import tools.jackson.databind.JsonNode;

/**
 * Greenhouse Job Board API (public, unauthenticated GETs): {@code https://boards-api.greenhouse.io/v1/boards/{token}/jobs}.
 *
 * <ul>
 *   <li>The list returns the whole board in one response with {@code meta.total}; it is complete when the counts match.
 *   <li>{@code content} is HTML-entity-encoded and is decoded here.
 *   <li>Pay ranges come only from the single-job endpoint ({@code pay_transparency=true}); they state no interval, so
 *       the period is left unknown.
 *   <li>Applying through the API needs the employer's key and is not used.
 * </ul>
 */
public final class GreenhouseJobBoardAdapter extends AbstractHttpJobSourceAdapter<GreenhouseSettings> {

    public static final String ID = "greenhouse-job-board";
    static final String BASE = "https://boards-api.greenhouse.io/v1/boards/";
    private static final Pattern JOB_ID = Pattern.compile("^\\d{1,20}$");

    public GreenhouseJobBoardAdapter(HttpFetcher fetcher, Clock clock) {
        super(fetcher, clock, GreenhouseSettings.class);
    }

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id(ID)
                .version("1.0.0")
                .kind(PluginKind.JOB_SOURCE)
                .capabilities(EnumSet.of(Capability.LIST_JOBS, Capability.JOB_DETAILS, Capability.AVAILABILITY_CHECK,
                        Capability.COMPENSATION))
                .settingsType(GreenhouseSettings.class)
                .settingsSchemaVersion(1)
                .compliance(new ComplianceNote(URI.create("https://developers.greenhouse.io/job-board.html"), false,
                        "Public Job Board API; GET endpoints need no authentication. Application submission requires the "
                                + "employer's API key and is not used."))
                .build();
    }

    @Override
    protected URI listUri(SourceBinding binding, GreenhouseSettings settings, String cursor, int pageSize) {
        return URI.create(BASE + binding.externalRef() + "/jobs?content=true");
    }

    @Override
    protected URI boardProbeUri(SourceBinding binding, GreenhouseSettings settings) {
        return URI.create(BASE + binding.externalRef() + "/jobs");
    }

    @Override
    protected URI jobUri(SourceBinding binding, GreenhouseSettings settings, String externalId) {
        if (!JOB_ID.matcher(externalId).matches()) {
            throw new IllegalArgumentException("Greenhouse job ids are numeric: " + externalId);
        }
        return URI.create(BASE + binding.externalRef() + "/jobs/" + externalId + "?pay_transparency=true");
    }

    @Override
    protected DiscoveryPage parsePage(JsonNode body, DiscoveryRequest request, GreenhouseSettings settings) {
        JsonNode jobsNode = body.get("jobs");
        if (jobsNode == null || !jobsNode.isArray()) {
            throw new ParseProblem("missing 'jobs' array");
        }
        List<SourceJob> jobs = new ArrayList<>();
        List<DiscoveryPage.ItemProblem> problems = new ArrayList<>();
        for (JsonNode job : jobsNode.values()) {
            try {
                jobs.add(toSourceJob(job));
            } catch (ParseProblem e) {
                problems.add(new DiscoveryPage.ItemProblem(text(job, "id"), e.getMessage()));
            }
        }
        JsonNode total = object(body, "meta") == null ? null : body.get("meta").get("total");
        boolean complete = total != null && total.isNumber() && total.asInt() == jobsNode.size();
        return new DiscoveryPage(jobs, null, complete, problems);
    }

    @Override
    protected SourceJob parseJob(JsonNode body, SourceJobRef ref, GreenhouseSettings settings) {
        return toSourceJob(body);
    }

    static SourceJob toSourceJob(JsonNode job) {
        String id = text(job, "id");
        String title = text(job, "title");
        if (id == null || title == null) {
            throw new ParseProblem("job without id or title");
        }
        JsonNode location = object(job, "location");
        List<RawLocation> locations = text(location, "name") == null
                ? List.of()
                : List.of(new RawLocation(text(location, "name"), null, null, null, null));
        String department = null;
        for (JsonNode d : array(job, "departments")) {
            department = text(d, "name");
            break;
        }
        String content = text(job, "content");
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "internalJobId", text(job, "internal_job_id"));
        putIfPresent(metadata, "requisitionId", text(job, "requisition_id"));
        putIfPresent(metadata, "companyName", text(job, "company_name"));
        putIfPresent(metadata, "language", text(job, "language"));
        putIfPresent(metadata, "applicationDeadline", text(job, "application_deadline"));
        String url = text(job, "absolute_url");
        return SourceJob.builder()
                .externalId(id)
                .title(title)
                .locations(locations)
                .department(department)
                .employmentTypeRaw(null) // not provided by the Job Board API
                .workplaceType(null)
                .descriptionHtml(content == null ? null : Parser.unescapeEntities(content, false))
                .listingUrl(url == null ? null : URI.create(url))
                .applicationUrl(url == null ? null : URI.create(url))
                .publishedAt(instant(job, "first_published"))
                .updatedAt(instant(job, "updated_at"))
                .compensation(compensation(job))
                .sourceMetadata(metadata)
                .build();
    }

    /** Combines pay ranges (amounts are in cents) when they share one currency; the interval is not stated. */
    static Compensation compensation(JsonNode job) {
        BigDecimal min = null;
        BigDecimal max = null;
        String currency = null;
        List<String> titles = new ArrayList<>();
        for (JsonNode range : array(job, "pay_input_ranges")) {
            String rangeCurrency = text(range, "currency_type");
            if (currency != null && rangeCurrency != null && !currency.equals(rangeCurrency)) {
                return new Compensation(null, null, null, null, "multiple currencies: " + String.join("; ", titles));
            }
            currency = rangeCurrency == null ? currency : rangeCurrency;
            BigDecimal low = cents(decimal(range, "min_cents"));
            BigDecimal high = cents(decimal(range, "max_cents"));
            min = low == null ? min : min == null ? low : min.min(low);
            max = high == null ? max : max == null ? high : max.max(high);
            if (text(range, "title") != null) {
                titles.add(text(range, "title"));
            }
        }
        if (min == null && max == null) {
            return null;
        }
        return new Compensation(min, max, currency, null, titles.isEmpty() ? null : String.join("; ", titles));
    }

    private static BigDecimal cents(BigDecimal cents) {
        return cents == null ? null : cents.movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
