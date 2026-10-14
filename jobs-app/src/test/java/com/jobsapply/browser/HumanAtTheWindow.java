package com.jobsapply.browser;

import com.microsoft.playwright.Page;
import java.util.UUID;
import java.util.function.Consumer;

/** Lets platform tests act as the human in an assistance window (e.g. press Submit). Test-only access. */
public final class HumanAtTheWindow {

    private HumanAtTheWindow() {}

    public static void act(PlaywrightApplicationAdapter adapter, UUID sessionId, Consumer<Page> action) {
        adapter.actAsHuman(sessionId, action);
    }
}
