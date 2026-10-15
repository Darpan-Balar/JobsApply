package com.jobsapply.platform;

import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.identity.UserRole;
import com.jobsapply.platform.support.ApiTest;
import com.jobsapply.platform.support.HttpApi;
import com.jobsapply.platform.support.TestAccounts;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * The real dashboard in a real (headless) browser against the real server: sign-in is required, each user sees only
 * their own data, deep links work, and the strict Content-Security-Policy holds (no violations).
 */
@ApiTest
class DashboardE2EIT {

    static Playwright playwright;
    static Browser browser;

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;

    @BeforeAll
    static void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void close() {
        if (playwright != null) {
            playwright.close();
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String account() {
        String username = uniqueKey("ui");
        accounts.account(username, UserRole.USER);
        return username;
    }

    /** A fresh browser profile (own cookies) that records console errors. */
    private Page newPage(List<String> consoleErrors) {
        BrowserContext context = browser.newContext();
        Page page = context.newPage();
        page.onConsoleMessage(message -> {
            if ("error".equals(message.type())) {
                consoleErrors.add(message.text());
            }
        });
        return page;
    }

    private void signIn(Page page, String username) {
        page.navigate(url("/"));
        page.getByLabel("Username").fill(username);
        page.getByLabel("Password").fill(TestAccounts.PASSWORD);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Overview")).waitFor();
    }

    @Test
    void signInIsRequiredAndEachUserSeesOnlyTheirOwnData() {
        List<String> errors = new ArrayList<>();
        String alice = account();
        String bob = account();
        String contactName = "Rita Recruiter " + uniqueKey("r");
        HttpApi.signedIn(port, alice, TestAccounts.PASSWORD).postJson("/api/v1/me/contacts", Map.of("name", contactName));

        Page anonymous = newPage(errors);
        Response home = anonymous.navigate(url("/tracking"));
        assertThat(home.status()).isEqualTo(200);
        assertThat(home.headers().get("content-security-policy")).contains("script-src 'self'");
        assertThat(anonymous.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Sign in")).isVisible()).isTrue();
        assertThat(anonymous.request().get(url("/api/v1/me/contacts")).status()).isEqualTo(401);

        Page alicePage = newPage(errors);
        signIn(alicePage, alice);
        alicePage.navigate(url("/tracking"));
        alicePage.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Contacts")).click();
        alicePage.getByText(contactName).waitFor();

        Page bobPage = newPage(errors);
        signIn(bobPage, bob);
        bobPage.navigate(url("/tracking"));
        bobPage.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Contacts")).click();
        bobPage.getByText("No contacts yet.").waitFor();
        assertThat(bobPage.content()).doesNotContain(contactName);
        assertThat(bobPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Admin")).count()).isZero();

        alicePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign out")).click();
        alicePage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Sign in")).waitFor();
        assertThat(alicePage.request().get(url("/api/v1/me/contacts")).status()).isEqualTo(401);

        assertThat(errors).as("no console errors (e.g. Content-Security-Policy violations)")
                .noneMatch(e -> e.contains("Content Security Policy") || e.contains("Refused"));
    }

    @Test
    void aContactAddedInTheDashboardIsStored() {
        String user = account();
        Page page = newPage(new ArrayList<>());
        signIn(page, user);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Tracking")).click();
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Contacts")).click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add contact")).click();
        page.getByLabel("Name").fill("Ian Interviewer");
        page.getByLabel("E-mail").fill("ian@example.org");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click();
        page.getByText("Ian Interviewer").waitFor();

        assertThat(HttpApi.signedIn(port, user, TestAccounts.PASSWORD).get("/api/v1/me/contacts").body()).contains("Ian Interviewer");
    }
}
