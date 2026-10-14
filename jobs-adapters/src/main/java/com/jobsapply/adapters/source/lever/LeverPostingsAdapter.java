package com.jobsapply.adapters.source.lever;

import static com.jobsapply.adapters.http.Json.array;
import static com.jobsapply.adapters.http.Json.decimal;
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
import com.jobsapply.spi.source.SourceJobRef;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.parser.Parser;
import tools.jackson.databind.JsonNode;

/**
 * Lever Postings API (public): {@code https://api.lever.co/v0/postings/{site}?mode=json&skip&limit}.
 *
 * <ul>
 *   <li>Paged with {@code skip}/{@code limit}. Only a board that fits in a single page is reported as a complete
 *       listing: offsets can shift while paging, so a multi-page union is not proof that a missing posting closed.
 *   <li>{@code createdAt} is not documented, so it is kept as metadata and never used as a publication date.
 *   <li>{@code workplaceType} is {@code onsite} in practice ({@code on-site} in the docs); both are accepted.
 *   <li>Applying through the API needs the employer's key and is not used.
 * </ul>
 */
public final class LeverPostingsAdapter extends AbstractHttpJobSourceAdapter<LeverSettings> {

    public static final String ID = "lever-postings";
    private static final Pattern POSTING_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    public LeverPostingsAdapter(HttpFetcher fetcher, Clock clock) {
        super(fetcher, clock, LeverSettings.class);
    }

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id(ID)
                .version("1.0.0")
                .kind(PluginKind.JOB_SOURCE)
                .capabilities(EnumSet.of(Capability.LIST_JOBS, Capability.JOB_DETAILS, Capability.AVAILABILITY_CHECK,
                        Capability.COMPENSATION))
                .settingsType(LeverSettings.class)
                .settingsSchemaVersion(1)
                .compliance(new ComplianceNote(URI.create("https://github.com/lever/postings-api"), false,
                        "Public Postings API; no authentication for GET. Application POSTs require the employer's API key "
                                + "and are rate limited by Lever; not used."))
                .build();
    }

    @Override
    protected URI listUri(SourceBinding binding, LeverSettings settings, String cursor, int pageSize) {
        int skip = cursor == null ? 0 : Integer.parseInt(cursor);
        return URI.create(settings.region().base + binding.externalRef() + "?mode=json&skip=" + skip + "&limit=" + settings.pageSize());
    }

    @Override
    protected URI boardProbeUri(SourceBinding binding, LeverSettings settings) {
        return URI.create(settings.region().base + binding.externalRef() + "?mode=json&limit=1");
    }

    @Override
    protected URI jobUri(SourceBinding binding, LeverSettings settings, String externalId) {
        if (!POSTING_ID.matcher(externalId).matches()) {
            throw new IllegalArgumentException("unexpected Lever posting id: " + externalId);
        }
        return URI.create(settings.region().base + binding.externalRef() + "/" + externalId + "?mode=json");
    }

    @Override
    protected DiscoveryPage parsePage(JsonNode body, DiscoveryRequest request, LeverSettings settings) {
        if (!body.isArray()) {
            throw new ParseProblem("expected a JSON array of postings");
        }
        int skip = request.cursor() == null ? 0 : Integer.parseInt(request.cursor());
        List<SourceJob> jobs = new ArrayList<>();
        List<DiscoveryPage.ItemProblem> problems = new ArrayList<>();
        for (JsonNode posting : body.values()) {
            try {
                jobs.add(toSourceJob(posting));
            } catch (ParseProblem e) {
                problems.add(new DiscoveryPage.ItemProblem(text(posting, "id"), e.getMessage()));
            }
        }
        int received = body.size();
        boolean lastPage = received < settings.pageSize();
        return new DiscoveryPage(jobs, lastPage ? null : String.valueOf(skip + received), lastPage && skip == 0, problems);
    }

    @Override
    protected SourceJob parseJob(JsonNode body, SourceJobRef ref, LeverSettings settings) {
        return toSourceJob(body);
    }

    static SourceJob toSourceJob(JsonNode posting) {
        String id = text(posting, "id");
        String title = text(posting, "text");
        if (id == null || title == null) {
            throw new ParseProblem("posting without id or text");
        }
        JsonNode categories = object(posting, "categories");
        String primary = text(categories, "location");
        String country = text(posting, "country");
        WorkplaceType workplace = workplaceType(text(posting, "workplaceType"));
        Boolean remote = workplace == WorkplaceType.REMOTE ? Boolean.TRUE : null;

        Set<String> names = new LinkedHashSet<>();
        if (primary != null) {
            names.add(primary);
        }
        for (JsonNode location : array(categories, "allLocations")) {
            if (location.isString() && !location.asString().isBlank()) {
                names.add(location.asString().strip());
            }
        }
        List<RawLocation> locations = new ArrayList<>();
        for (String name : names) {
            // `country` describes the posting's primary location only.
            String code = name.equals(primary) ? Countries.toAlpha2(country).orElse(null) : null;
            locations.add(new RawLocation(name, null, null, code, remote));
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "team", text(categories, "team"));
        putIfPresent(metadata, "commitment", text(categories, "commitment"));
        putIfPresent(metadata, "workplaceTypeRaw", text(posting, "workplaceType"));
        putIfPresent(metadata, "country", country);
        if (posting.get("createdAt") != null && posting.get("createdAt").isNumber()) {
            metadata.put("createdAtRaw", posting.get("createdAt").asLong()); // undocumented; informational only
        }
        String department = text(categories, "department") != null ? text(categories, "department") : text(categories, "team");
        String hosted = text(posting, "hostedUrl");
        String apply = text(posting, "applyUrl");
        return SourceJob.builder()
                .externalId(id)
                .title(title)
                .locations(locations)
                .department(department)
                .employmentTypeRaw(text(categories, "commitment"))
                .workplaceType(workplace)
                .descriptionHtml(descriptionHtml(posting))
                .listingUrl(hosted == null ? null : URI.create(hosted))
                .applicationUrl(apply == null ? null : URI.create(apply))
                .publishedAt(null)
                .updatedAt(null)
                .compensation(compensation(posting))
                .sourceMetadata(metadata)
                .build();
    }

    static WorkplaceType workplaceType(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "onsite", "on-site" -> WorkplaceType.ONSITE;
            case "remote" -> WorkplaceType.REMOTE;
            case "hybrid" -> WorkplaceType.HYBRID;
            default -> null; // "unspecified" or unknown values: say nothing
        };
    }

    private static String descriptionHtml(JsonNode posting) {
        StringBuilder html = new StringBuilder();
        if (text(posting, "description") != null) {
            html.append(text(posting, "description"));
        }
        for (JsonNode list : array(posting, "lists")) {
            html.append("<h3>").append(Parser.unescapeEntities(nullToEmpty(text(list, "text")), false)).append("</h3>")
                    .append("<ul>").append(nullToEmpty(text(list, "content"))).append("</ul>");
        }
        if (text(posting, "additional") != null) {
            html.append(text(posting, "additional"));
        }
        return html.isEmpty() ? null : html.toString();
    }

    static Compensation compensation(JsonNode posting) {
        JsonNode range = object(posting, "salaryRange");
        String description = text(posting, "salaryDescriptionPlain");
        if (range == null && description == null) {
            return null;
        }
        return new Compensation(decimal(range, "min"), decimal(range, "max"), text(range, "currency"),
                period(text(range, "interval")), description);
    }

    /** {@code per-year-salary} → YEAR, {@code per-hour-wage} → HOUR, ...; unknown → {@code null}. */
    static CompensationPeriod period(String interval) {
        if (interval == null) {
            return null;
        }
        String value = interval.toLowerCase(Locale.ROOT);
        if (value.contains("year")) {
            return CompensationPeriod.YEAR;
        }
        if (value.contains("month")) {
            return CompensationPeriod.MONTH;
        }
        if (value.contains("week")) {
            return CompensationPeriod.WEEK;
        }
        if (value.contains("day")) {
            return CompensationPeriod.DAY;
        }
        if (value.contains("hour")) {
            return CompensationPeriod.HOUR;
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
