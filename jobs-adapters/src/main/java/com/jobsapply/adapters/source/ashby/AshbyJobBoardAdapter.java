package com.jobsapply.adapters.source.ashby;

import static com.jobsapply.adapters.http.Json.array;
import static com.jobsapply.adapters.http.Json.decimal;
import static com.jobsapply.adapters.http.Json.instant;
import static com.jobsapply.adapters.http.Json.object;
import static com.jobsapply.adapters.http.Json.text;

import com.jobsapply.adapters.http.AbstractHttpJobSourceAdapter;
import com.jobsapply.adapters.http.HttpFetcher;
import com.jobsapply.spi.common.Capability;
import com.jobsapply.spi.common.CompensationPeriod;
import com.jobsapply.spi.common.ComplianceNote;
import com.jobsapply.spi.common.Countries;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.common.WorkplaceType;
import com.jobsapply.spi.source.Compensation;
import com.jobsapply.spi.source.DiscoveryPage;
import com.jobsapply.spi.source.DiscoveryRequest;
import com.jobsapply.spi.source.RawLocation;
import com.jobsapply.spi.source.SourceBinding;
import com.jobsapply.spi.source.SourceJob;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Ashby public job posting API: {@code https://api.ashbyhq.com/posting-api/job-board/{name}?includeCompensation=true}.
 *
 * <ul>
 *   <li>One response contains every published posting, so the listing is complete.
 *   <li>Postings with {@code isListed=false} are only reachable by direct link and are skipped.
 *   <li>The docs show no job id, but responses carry {@code id}; when absent the id is the last {@code jobUrl}
 *       segment.
 *   <li>There is no single-job endpoint: details and availability checks are unsupported (the complete listing is the
 *       availability evidence).
 *   <li>Only {@code Salary} compensation components are used; bonus/commission/equity are ignored.
 * </ul>
 */
public final class AshbyJobBoardAdapter extends AbstractHttpJobSourceAdapter<AshbySettings> {

    public static final String ID = "ashby-job-board";
    static final String BASE = "https://api.ashbyhq.com/posting-api/job-board/";

    public AshbyJobBoardAdapter(HttpFetcher fetcher, Clock clock) {
        super(fetcher, clock, AshbySettings.class);
    }

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id(ID)
                .version("1.0.0")
                .kind(PluginKind.JOB_SOURCE)
                .capabilities(EnumSet.of(Capability.LIST_JOBS, Capability.COMPENSATION))
                .settingsType(AshbySettings.class)
                .settingsSchemaVersion(1)
                .compliance(new ComplianceNote(URI.create("https://developers.ashbyhq.com/docs/public-job-posting-api"), false,
                        "Public job posting API; no authentication. Candidates apply on Ashby's hosted page."))
                .build();
    }

    @Override
    protected URI listUri(SourceBinding binding, AshbySettings settings, String cursor, int pageSize) {
        return URI.create(BASE + binding.externalRef() + (settings.includeCompensation() ? "?includeCompensation=true" : ""));
    }

    @Override
    protected DiscoveryPage parsePage(JsonNode body, DiscoveryRequest request, AshbySettings settings) {
        JsonNode jobsNode = body.get("jobs");
        if (jobsNode == null || !jobsNode.isArray()) {
            throw new ParseProblem("missing 'jobs' array");
        }
        List<SourceJob> jobs = new ArrayList<>();
        List<DiscoveryPage.ItemProblem> problems = new ArrayList<>();
        for (JsonNode job : jobsNode.values()) {
            JsonNode listed = job.get("isListed");
            if (listed != null && listed.isBoolean() && !listed.asBoolean()) {
                continue;
            }
            try {
                jobs.add(toSourceJob(job));
            } catch (ParseProblem e) {
                problems.add(new DiscoveryPage.ItemProblem(text(job, "id"), e.getMessage()));
            }
        }
        return new DiscoveryPage(jobs, null, true, problems);
    }

    static SourceJob toSourceJob(JsonNode job) {
        String jobUrl = text(job, "jobUrl");
        String id = text(job, "id") != null ? text(job, "id") : lastPathSegment(jobUrl);
        String title = text(job, "title");
        if (id == null || title == null) {
            throw new ParseProblem("posting without id/jobUrl or title");
        }
        boolean remote = job.get("isRemote") != null && job.get("isRemote").asBoolean(false);
        List<RawLocation> locations = new ArrayList<>();
        if (text(job, "location") != null) {
            locations.add(location(text(job, "location"), object(job, "address"), remote));
        }
        for (JsonNode secondary : array(job, "secondaryLocations")) {
            if (text(secondary, "location") != null) {
                locations.add(location(text(secondary, "location"), object(secondary, "address"), null));
            }
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (text(job, "team") != null) {
            metadata.put("team", text(job, "team"));
        }
        metadata.put("isRemote", remote);
        String apply = text(job, "applyUrl");
        return SourceJob.builder()
                .externalId(id)
                .title(title)
                .locations(locations)
                .department(text(job, "department"))
                .employmentTypeRaw(text(job, "employmentType"))
                .workplaceType(workplaceType(text(job, "workplaceType")))
                .descriptionHtml(text(job, "descriptionHtml"))
                .listingUrl(jobUrl == null ? null : URI.create(jobUrl))
                .applicationUrl(apply == null ? null : URI.create(apply))
                .publishedAt(instant(job, "publishedAt"))
                .updatedAt(null)
                .compensation(compensation(object(job, "compensation")))
                .sourceMetadata(metadata)
                .build();
    }

    /** Address may be nested under {@code postalAddress} (primary) or flat (secondary locations). */
    private static RawLocation location(String text, JsonNode address, Boolean remote) {
        JsonNode postal = object(address, "postalAddress") != null ? object(address, "postalAddress") : address;
        return new RawLocation(text, text(postal, "addressLocality"), text(postal, "addressRegion"),
                Countries.toAlpha2(text(postal, "addressCountry")).orElse(null), remote);
    }

    static WorkplaceType workplaceType(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "onsite", "on-site" -> WorkplaceType.ONSITE;
            case "remote" -> WorkplaceType.REMOTE;
            case "hybrid" -> WorkplaceType.HYBRID;
            default -> null;
        };
    }

    static Compensation compensation(JsonNode compensation) {
        if (compensation == null) {
            return null;
        }
        String summary = text(compensation, "compensationTierSummary");
        for (JsonNode component : array(compensation, "summaryComponents")) {
            if ("Salary".equalsIgnoreCase(text(component, "compensationType"))
                    && (decimal(component, "minValue") != null || decimal(component, "maxValue") != null)) {
                return new Compensation(decimal(component, "minValue"), decimal(component, "maxValue"),
                        text(component, "currencyCode"), period(text(component, "interval")), summary);
            }
        }
        return summary == null ? null : new Compensation(null, null, null, null, summary);
    }

    /** {@code "1 YEAR"} → YEAR; other multiples or units → {@code null}. */
    static CompensationPeriod period(String interval) {
        if (interval == null) {
            return null;
        }
        return switch (interval.strip().toUpperCase(Locale.ROOT)) {
            case "1 YEAR" -> CompensationPeriod.YEAR;
            case "1 MONTH" -> CompensationPeriod.MONTH;
            case "1 WEEK" -> CompensationPeriod.WEEK;
            case "1 DAY" -> CompensationPeriod.DAY;
            case "1 HOUR" -> CompensationPeriod.HOUR;
            default -> null;
        };
    }

    private static String lastPathSegment(String url) {
        if (url == null) {
            return null;
        }
        String path = URI.create(url).getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        String[] parts = path.split("/");
        return parts.length == 0 || parts[parts.length - 1].isBlank() ? null : parts[parts.length - 1];
    }
}
