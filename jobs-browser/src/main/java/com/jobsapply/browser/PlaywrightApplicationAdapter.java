package com.jobsapply.browser;

import com.jobsapply.spi.application.ApplicationAdapter;
import com.jobsapply.spi.application.ApplicationDestination;
import com.jobsapply.spi.application.ApplicationSession;
import com.jobsapply.spi.application.Blocker;
import com.jobsapply.spi.application.ConfirmationEvidence;
import com.jobsapply.spi.application.FieldMappingPlan;
import com.jobsapply.spi.application.FieldMappingPlan.FieldMapping;
import com.jobsapply.spi.application.FillReport;
import com.jobsapply.spi.application.FormInspection;
import com.jobsapply.spi.common.Capability;
import com.jobsapply.spi.common.ComplianceNote;
import com.jobsapply.spi.common.FailureKind;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.PluginSettings;
import com.jobsapply.spi.common.ValidationReport;
import com.jobsapply.spi.render.ApplicationQuestion;
import com.jobsapply.spi.render.ApplicationQuestion.QuestionType;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.SelectOption;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic browser assistance with Playwright: opens the application page in a window the human sees, reads the form
 * (including embedded frames), fills only HIGH-confidence mappings into fields that are still empty, and watches for a
 * confirmation after the human presses Submit. It never clicks submit, never touches CAPTCHA widgets, and stops on
 * challenge pages, logins and one-time codes.
 *
 * <p>Playwright objects are not thread-safe, so every browser call runs on one dedicated thread. Each session gets its
 * own ephemeral browser context: nothing (cookies, storage, passwords) is kept after the window closes.
 */
public final class PlaywrightApplicationAdapter implements ApplicationAdapter, AutoCloseable {

    public static final String ID = "playwright-browser";

    private static final Pattern CONFIRMATION = Pattern.compile(
            "thank(s| you) for (applying|your application|submitting)"
                    + "|application (has been |was )?(successfully )?(submitted|received)"
                    + "|we('ve| have) received your application"
                    + "|your application (has been|was) (successfully )?(submitted|received|sent)"
                    + "|successfully (applied|submitted)",
            Pattern.CASE_INSENSITIVE);
    private static final Duration POLL = Duration.ofMillis(500);
    private static final int MAX_DETAIL = 300;

    private final BrowserSettings settings;
    private final Clock clock;
    private final String inspectScript;
    private final ExecutorService browserThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "playwright-browser");
        t.setDaemon(true);
        return t;
    });
    /** Session id → window. Only read or written on the browser thread. */
    private final Map<UUID, Window> windows = new HashMap<>();
    private Playwright playwright;
    private Browser browser;

    public PlaywrightApplicationAdapter(BrowserSettings settings, Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.inspectScript = resource("inspect-form.js");
    }

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder().id(ID).version("1.0.0").kind(PluginKind.APPLICATION_ADAPTER)
                .capabilities(EnumSet.of(Capability.OPEN_DESTINATION, Capability.INSPECT_FIELDS, Capability.FILL_FIELDS,
                        Capability.VALIDATE_FORM, Capability.DETECT_CONFIRMATION))
                .settingsType(PluginSettings.None.class).settingsSchemaVersion(1)
                .compliance(new ComplianceNote(null, false, "visible browser; the human submits; CAPTCHA, MFA and logins are "
                        + "left to the human (Playwright, Apache-2.0)"))
                .build();
    }

    @Override
    public boolean supports(ApplicationDestination destination) {
        URI url = destination == null ? null : destination.applicationUrl();
        return url != null && url.getHost() != null && ("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()));
    }

    @Override
    public PluginResult<FormInspection> open(ApplicationSession session) {
        if (!supports(session.destination())) {
            return PluginResult.failed(FailureKind.PERMANENT, "not an http(s) application link");
        }
        PluginResult<Boolean> launched = onBrowser(settings.launchTimeout(), () -> {
            browser();
            return PluginResult.ok(Boolean.TRUE);
        });
        if (launched instanceof PluginResult.Failed<Boolean> f) {
            return PluginResult.failed(f.kind(), "the browser could not start: " + f.message());
        }
        return onBrowser(settings.navigationTimeout().plus(settings.actionTimeout()), () -> {
            closeWindow(session.id());
            BrowserContext context = browser().newContext(new Browser.NewContextOptions().setAcceptDownloads(false));
            context.setDefaultTimeout(settings.actionTimeout().toMillis());
            context.setDefaultNavigationTimeout(settings.navigationTimeout().toMillis());
            Page page = context.newPage();
            Window window = new Window(context, page);
            windows.put(session.id(), window);
            Response response = page.navigate(session.destination().applicationUrl().toString());
            window.status = response == null ? 0 : response.status();
            if (window.status == 404 || window.status == 410) {
                closeWindow(session.id());
                return PluginResult.failed(FailureKind.PERMANENT, "the application page answered HTTP " + window.status);
            }
            page.waitForLoadState();
            FormInspection inspection = inspect(window);
            window.formUrl = page.url();
            window.formKeys = Set.copyOf(window.handles.keySet());
            return PluginResult.ok(inspection);
        });
    }

    @Override
    public PluginResult<FormInspection> inspect(ApplicationSession session) {
        return onBrowser(settings.actionTimeout().multipliedBy(2), () -> withWindow(session, window -> {
            FormInspection inspection = inspect(window);
            if (!inspection.blocked() && window.formKeys.isEmpty()) {
                window.formUrl = window.page.url();
                window.formKeys = Set.copyOf(window.handles.keySet());
            }
            return PluginResult.ok(inspection);
        }));
    }

    @Override
    public PluginResult<FillReport> fill(ApplicationSession session, FieldMappingPlan plan) {
        Duration budget = settings.actionTimeout().multipliedBy(Math.max(2, plan.mappings().size() + 1));
        return onBrowser(budget, () -> withWindow(session, window -> {
            FormInspection fresh = inspect(window);
            List<String> filled = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            if (fresh.blocked()) {
                plan.mappings().forEach(m -> skipped.add(m.fieldId()));
                return PluginResult.ok(new FillReport(filled, skipped, fresh.blockers()));
            }
            for (FieldMapping mapping : plan.mappings()) {
                Handle handle = window.handles.get(mapping.fieldId());
                if (mapping.confidence() != FieldMappingPlan.Confidence.HIGH || handle == null || handle.hasValue()) {
                    skipped.add(mapping.fieldId());
                    continue;
                }
                try {
                    (fillOne(handle, mapping) ? filled : skipped).add(mapping.fieldId());
                } catch (PlaywrightException e) {
                    skipped.add(mapping.fieldId());
                }
            }
            inspect(window);
            return PluginResult.ok(new FillReport(filled, skipped, List.of()));
        }));
    }

    @Override
    public PluginResult<ValidationReport> validate(ApplicationSession session) {
        return onBrowser(settings.actionTimeout().multipliedBy(2), () -> withWindow(session, window -> {
            inspect(window);
            List<ValidationReport.Issue> issues = new ArrayList<>();
            for (Handle h : window.handles.values()) {
                if (h.required() && !h.hasValue()) {
                    issues.add(new ValidationReport.Issue(h.key(), "required and still empty"));
                }
            }
            for (Frame frame : window.page.frames()) {
                Object invalid = frame.evaluate("() => Array.from(document.querySelectorAll('[aria-invalid=\"true\"]'))"
                        + ".map(e => e.id || e.name).filter(Boolean)");
                if (invalid instanceof List<?> keys) {
                    keys.forEach(k -> issues.add(new ValidationReport.Issue(String.valueOf(k), "the form marks this field as invalid")));
                }
            }
            return PluginResult.ok(new ValidationReport(issues));
        }));
    }

    /**
     * Polls (from the caller's thread, so the browser thread stays free) for a confirmation message on a page whose form
     * is gone or whose address changed. Only that counts as evidence; anything else is {@link ConfirmationEvidence.Kind#NONE}.
     */
    @Override
    public PluginResult<ConfirmationEvidence> observeConfirmation(ApplicationSession session, Duration timeout) {
        Instant deadline = clock.instant().plus(timeout);
        while (true) {
            PluginResult<Observation> result = onBrowser(settings.actionTimeout().multipliedBy(2), () -> observe(windows.get(session.id())));
            if (!(result instanceof PluginResult.Ok<Observation>(Observation seen))) {
                return switch (result) {
                    case PluginResult.Failed<Observation> f -> PluginResult.failed(f.kind(), f.message());
                    case PluginResult.Unsupported<Observation> u -> PluginResult.unsupported(u.capability());
                    case PluginResult.Ok<Observation> ok -> throw new IllegalStateException();
                };
            }
            if (seen.windowClosed()) {
                return PluginResult.ok(none("the window was closed before a confirmation was seen"));
            }
            if (seen.detail() != null) {
                String artifact = seen.screenshot() == null ? null : session.saveArtifact("image/png", seen.screenshot());
                return PluginResult.ok(new ConfirmationEvidence(ConfirmationEvidence.Kind.CONFIRMATION_PAGE, seen.detail(),
                        safeUri(seen.url()), artifact, clock.instant()));
            }
            if (!clock.instant().isBefore(deadline)) {
                return PluginResult.ok(none("no confirmation seen within " + timeout.toSeconds() + " s"));
            }
            try {
                Thread.sleep(POLL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return PluginResult.ok(none("observation interrupted"));
            }
        }
    }

    @Override
    public void close(ApplicationSession session) {
        onBrowser(settings.actionTimeout(), () -> {
            closeWindow(session.id());
            return PluginResult.ok(Boolean.TRUE);
        });
    }

    @Override
    public void close() {
        try {
            browserThread.submit(() -> {
                List.copyOf(windows.keySet()).forEach(this::closeWindow);
                if (browser != null) {
                    browser.close();
                }
                if (playwright != null) {
                    playwright.close();
                }
                return null;
            }).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            // Shutting down: the browser process dies with the JVM.
        } finally {
            browserThread.shutdownNow();
        }
    }

    /** Test hook standing in for the human (e.g. pressing Submit). Not part of the plugin contract. */
    void actAsHuman(UUID sessionId, java.util.function.Consumer<Page> action) {
        PluginResult<Boolean> result = onBrowser(settings.navigationTimeout(), () -> {
            action.accept(windows.get(sessionId).page);
            return PluginResult.ok(Boolean.TRUE);
        });
        if (!(result instanceof PluginResult.Ok<Boolean>)) {
            throw new IllegalStateException(String.valueOf(result));
        }
    }

    // --- browser-thread internals ------------------------------------------------------------------------------------

    private Browser browser() {
        if (browser == null || !browser.isConnected()) {
            if (playwright == null) {
                playwright = Playwright.create();
            }
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(settings.headless());
            if (settings.channel() != null) {
                options.setChannel(settings.channel());
            }
            browser = playwright.chromium().launch(options);
        }
        return browser;
    }

    private <T> PluginResult<T> withWindow(ApplicationSession session, WindowAction<T> action) {
        Window window = windows.get(session.id());
        if (window == null || window.page.isClosed()) {
            closeWindow(session.id());
            return PluginResult.failed(FailureKind.PERMANENT, "the assistance window is not open");
        }
        return action.apply(window);
    }

    /** Tolerates a missing window: that is reported as closed. */
    private PluginResult<Observation> observe(Window window) {
        if (window == null || window.page.isClosed()) {
            return PluginResult.ok(new Observation(true, null, null, null));
        }
        Page page = window.page;
        String text = String.join("\n", page.frames().stream().map(f -> {
            try {
                return String.valueOf(f.evaluate("() => document.body ? document.body.innerText.slice(0, 100000) : ''"));
            } catch (PlaywrightException e) {
                return "";
            }
        }).toList());
        Matcher match = CONFIRMATION.matcher(text);
        if (!match.find()) {
            return PluginResult.ok(new Observation(false, null, null, null));
        }
        inspect(window);
        long remaining = window.formKeys.stream().filter(window.handles::containsKey).count();
        boolean formGone = window.formKeys.isEmpty() || remaining * 2 < window.formKeys.size();
        boolean moved = window.formUrl != null && !window.formUrl.equals(page.url());
        if (!formGone && !moved) {
            return PluginResult.ok(new Observation(false, null, null, null));
        }
        byte[] screenshot = null;
        try {
            screenshot = page.screenshot();
        } catch (PlaywrightException e) {
            // Evidence without a screenshot is still evidence; the message and address are recorded.
        }
        return PluginResult.ok(new Observation(false, lineAround(text, match.start(), match.end()), page.url(), screenshot));
    }

    private FormInspection inspect(Window window) {
        Map<String, Handle> handles = new LinkedHashMap<>();
        List<ApplicationQuestion> fields = new ArrayList<>();
        boolean password = false;
        boolean otp = false;
        boolean captcha = false;
        boolean challenge = false;
        for (Frame frame : window.page.frames()) {
            if (frame.isDetached()) {
                continue;
            }
            String frameUrl = frame.url().toLowerCase(Locale.ROOT);
            if (frameUrl.contains("recaptcha") || frameUrl.contains("hcaptcha") || frameUrl.contains("challenges.cloudflare.com")) {
                captcha = true;
                continue;
            }
            Map<?, ?> result;
            try {
                result = (Map<?, ?>) frame.evaluate(inspectScript);
            } catch (PlaywrightException e) {
                continue;
            }
            password |= Boolean.TRUE.equals(result.get("password"));
            otp |= Boolean.TRUE.equals(result.get("otp"));
            captcha |= Boolean.TRUE.equals(result.get("captcha"));
            challenge |= Boolean.TRUE.equals(result.get("challenge"));
            for (Object raw : (List<?>) result.get("fields")) {
                Handle handle = Handle.of(frame, (Map<?, ?>) raw);
                if (handles.putIfAbsent(handle.key(), handle) == null) {
                    fields.add(handle.question());
                }
            }
        }
        window.handles = handles;
        List<Blocker> blockers = new ArrayList<>();
        List<String> humanSteps = new ArrayList<>();
        if (window.status == 401 || window.status == 403 || window.status == 429) {
            blockers.add(Blocker.ACCESS_DENIED);
        }
        if (challenge) {
            blockers.add(Blocker.CAPTCHA);
        }
        if (password) {
            blockers.add(Blocker.LOGIN_REQUIRED);
        }
        if (otp) {
            blockers.add(Blocker.MFA);
        }
        if (blockers.isEmpty() && fields.isEmpty()) {
            blockers.add(Blocker.UNRECOGNIZED_FORM);
        }
        if (captcha && !challenge) {
            humanSteps.add("The form has a CAPTCHA. Complete it yourself; it is never touched automatically.");
        }
        return new FormInspection(safeUri(window.page.url()), fields, blockers, humanSteps);
    }

    private static boolean fillOne(Handle handle, FieldMapping mapping) {
        Locator target = handle.frame().locator(handle.selector()).nth(handle.index());
        String value = mapping.value();
        switch (handle.control()) {
            case "text", "email", "tel", "url", "number", "date", "textarea" -> {
                if (value == null) {
                    return false;
                }
                target.fill(value);
                return true;
            }
            case "select" -> {
                Optional<String> option = handle.options().stream().map(Option::label).filter(l -> l.equalsIgnoreCase(value)).findFirst();
                option.ifPresent(label -> target.selectOption(new SelectOption().setLabel(label)));
                return option.isPresent();
            }
            case "radio" -> {
                Optional<Option> option = handle.options().stream().filter(o -> o.label().equalsIgnoreCase(value)).findFirst();
                option.ifPresent(o -> handle.frame().locator(o.selector()).nth(o.index()).check());
                return option.isPresent();
            }
            case "checkbox" -> {
                if (!"yes".equalsIgnoreCase(value) && !"true".equalsIgnoreCase(value)) {
                    return false;
                }
                target.check();
                return true;
            }
            case "file" -> {
                if (mapping.attachment() == null) {
                    return false;
                }
                FieldMappingPlan.Attachment file = mapping.attachment();
                target.setInputFiles(new FilePayload(file.fileName(), file.mediaType(), file.content()));
                return true;
            }
            default -> {
                // combobox widgets, multi-selects and checkbox groups are left to the human
                return false;
            }
        }
    }

    private void closeWindow(UUID sessionId) {
        Window window = windows.remove(sessionId);
        if (window != null) {
            try {
                window.context.close();
            } catch (PlaywrightException e) {
                // already gone
            }
        }
    }

    private <T> PluginResult<T> onBrowser(Duration budget, Callable<PluginResult<T>> task) {
        Future<PluginResult<T>> future = browserThread.submit(task);
        try {
            return future.get(budget.toMillis() + 5_000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return PluginResult.failed(FailureKind.TRANSIENT, "the browser did not respond in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PluginResult.failed(FailureKind.TRANSIENT, "interrupted");
        } catch (ExecutionException e) {
            return PluginResult.failed(FailureKind.TRANSIENT, "browser error: " + describe(e.getCause()));
        }
    }

    /** Exception type and first line only (Playwright messages can be long call logs). */
    private static String describe(Throwable t) {
        String message = t.getMessage() == null ? "" : t.getMessage().lines().findFirst().orElse("");
        return t.getClass().getSimpleName() + (message.isBlank() ? "" : ": " + (message.length() > 200 ? message.substring(0, 200) : message));
    }

    private static String lineAround(String text, int start, int end) {
        int from = text.lastIndexOf('\n', start) + 1;
        int to = text.indexOf('\n', end);
        String line = text.substring(from, to < 0 ? text.length() : to).strip();
        return line.length() <= MAX_DETAIL ? line : line.substring(0, MAX_DETAIL);
    }

    private static URI safeUri(String url) {
        try {
            return url == null ? null : URI.create(url);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ConfirmationEvidence none(String why) {
        return new ConfirmationEvidence(ConfirmationEvidence.Kind.NONE, why, null, null, Instant.now());
    }

    private static String resource(String name) {
        try (InputStream in = PlaywrightApplicationAdapter.class.getResourceAsStream(name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // --- state ---------------------------------------------------------------------------------------------------------

    private static final class Window {
        final BrowserContext context;
        final Page page;
        Map<String, Handle> handles = Map.of();
        Set<String> formKeys = Set.of();
        String formUrl;
        int status;

        Window(BrowserContext context, Page page) {
            this.context = context;
            this.page = page;
        }
    }

    @FunctionalInterface
    private interface WindowAction<T> {
        PluginResult<T> apply(Window window);
    }

    private record Observation(boolean windowClosed, String detail, String url, byte[] screenshot) {}

    private record Option(String label, String selector, int index) {}

    private record Handle(Frame frame, String key, String control, String label, boolean required, boolean hasValue, String selector,
            int index, List<Option> options) {

        static Handle of(Frame frame, Map<?, ?> raw) {
            List<Option> options = new ArrayList<>();
            for (Object o : (List<?>) raw.get("options")) {
                Map<?, ?> option = (Map<?, ?>) o;
                options.add(new Option(String.valueOf(option.get("label")), (String) option.get("selector"),
                        option.get("index") instanceof Number n ? n.intValue() : 0));
            }
            return new Handle(frame, String.valueOf(raw.get("key")), String.valueOf(raw.get("control")), String.valueOf(raw.get("label")),
                    Boolean.TRUE.equals(raw.get("required")), Boolean.TRUE.equals(raw.get("hasValue")), (String) raw.get("selector"),
                    raw.get("index") instanceof Number n ? n.intValue() : 0, List.copyOf(options));
        }

        ApplicationQuestion question() {
            QuestionType type = switch (control) {
                case "textarea" -> QuestionType.TEXTAREA;
                case "select", "radio", "combobox" -> QuestionType.SINGLE_SELECT;
                case "multiselect", "checkboxes" -> QuestionType.MULTI_SELECT;
                case "checkbox" -> QuestionType.BOOLEAN;
                case "number" -> QuestionType.NUMBER;
                case "date" -> QuestionType.DATE;
                case "file" -> QuestionType.FILE;
                default -> QuestionType.TEXT;
            };
            return new ApplicationQuestion(key, label, type, required, options.stream().map(Option::label).toList());
        }
    }
}
