package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.web.stores.StoreRow;
import pl.commercelink.web.stores.StoreState;
import pl.commercelink.web.stores.StoresListPage;
import pl.commercelink.web.stores.StoresListQuery;
import pl.commercelink.web.stores.StoresNotice;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class StoresTemplateRenderingTest {

    private static final String STORE_ID = "abc123def4";

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

    private static StoreRow row(StoreState state, String noteDate, boolean demo, boolean trial, boolean canConvert,
                                boolean canDeactivate, boolean canActivate, boolean blocked) {
        return new StoreRow(STORE_ID, "abc123de", "My store", "Firma sp. z o.o.", "owner@example.com", state, noteDate, 9,
                "28.09.2026", demo, trial, canConvert, canDeactivate, canActivate, blocked, "/dashboard/stores?delete=" + STORE_ID);
    }

    private static List<StoresListPage.SegmentLink> segments() {
        return List.of(new StoresListPage.SegmentLink("stores.list.segment.all", 1, "/dashboard/stores", true),
                new StoresListPage.SegmentLink("stores.list.segment.full", 0, "/dashboard/stores?status=full", false));
    }

    private String render(StoresListPage page, StoresNotice notice) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("page", page);
        context.setVariable("storesNotice", notice);
        return engine.process("stores", context);
    }

    private static StoresListPage page(StoreRow row) {
        return new StoresListPage(StoresListQuery.defaults(), List.of(row), segments(), null, null);
    }

    @Test
    void trialRowShowsPillNoteAndConversion() {
        // when
        String html = render(page(row(StoreState.TRIAL, "12.10.2026", false, true, true, true, false, false)), null);

        // then
        assertThat(html).contains("cl-status is-info").contains(">Trial<").contains("Until: 12.10.2026 · days: 9");
        assertThat(html).contains("href=\"/dashboard/store/" + STORE_ID + "/trial/convert?back=");
        assertThat(html).contains("href=\"/dashboard/store/" + STORE_ID + "/deactivate?back=");
        assertThat(html).doesNotContain("/activate?");
        assertThat(html).contains("data-owner-email=\"owner@example.com\"").contains("data-store-trial=\"true\"");
    }

    @Test
    void endingTrialIsWarnedAbout() {
        // when
        String html = render(page(row(StoreState.TRIAL_ENDING, "15.10.2026", false, true, true, true, false, false)), null);

        // then
        assertThat(html).contains("cl-status is-warn").contains("cl-due-note is-warn");
    }

    @Test
    void endedTrialShowsDeletionAndBlockedActivation() {
        // when
        String html = render(page(row(StoreState.TRIAL_ENDED, "26.10.2026", false, true, true, false, false, true)), null);

        // then
        assertThat(html).contains("cl-status is-bad").contains(">Trial ended<").contains("Deletion: 26.10.2026");
        assertThat(html).contains("aria-disabled=\"true\"").contains("The trial has ended");
        assertThat(html).doesNotContain("/deactivate?").doesNotContain("/activate?");
        assertThat(html).contains("/trial/convert?");
    }

    @Test
    void switchedOffStoreOffersActivation() {
        // when
        String html = render(page(row(StoreState.SWITCHED_OFF, "03.10.2026", false, false, false, false, true, false)), null);

        // then
        assertThat(html).contains(">Switched off<").contains("Since: 03.10.2026");
        assertThat(html).contains("href=\"/dashboard/store/" + STORE_ID + "/activate?back=");
        assertThat(html).doesNotContain("/trial/convert");
    }

    @Test
    void fullAccountHasNoNoteAndNoConversion() {
        // when
        String html = render(page(row(StoreState.FULL, null, false, false, false, true, false, false)), null);

        // then
        assertThat(html).contains("cl-status is-ok").contains(">Full account<");
        assertThat(html).doesNotContain("cl-due-note").doesNotContain("/trial/convert");
        assertThat(html).contains("href=\"/dashboard/store/" + STORE_ID + "\"");
        assertThat(html).contains("href=\"/dashboard/store/" + STORE_ID + "/copy\"");
    }

    @Test
    void regularStoreDeleteOpensTypedDialogAndDemoUsesPlainConfirmation() {
        // when
        String regular = render(page(row(StoreState.FULL, null, false, false, false, true, false, false)), null);
        String demo = render(page(row(StoreState.DEMO, "17.10.2026", true, false, false, true, false, false)), null);

        // then
        assertThat(regular).contains("data-cl-dialog-open=\"store-delete-dialog\"")
                .contains("href=\"/dashboard/stores?delete=" + STORE_ID + "\"");
        assertThat(demo).doesNotContain("data-cl-dialog-open=\"store-delete-dialog\"")
                .contains("href=\"/dashboard/store/" + STORE_ID + "/delete?back=");
    }

    @Test
    void deleteDialogRenderedOpenForServerSideRequest() {
        // given
        StoresListPage page = new StoresListPage(StoresListQuery.of(null, null, null, null, STORE_ID),
                List.of(row(StoreState.TRIAL, "12.10.2026", false, true, true, true, false, false)), segments(),
                new StoresListPage.DeleteDialog(STORE_ID, "My store", true, "owner@example.com", "/dashboard/store/" + STORE_ID + "/delete"),
                null);

        // when
        String html = render(page, null);

        // then
        assertThat(html).containsPattern("<dialog[^>]*id=\"store-delete-dialog\"[^>]*open=\"open\"");
        assertThat(html).contains("Delete store “My store”?");
        assertThat(html).contains("the owner account owner@example.com (panel sign-in)");
        assertThat(html).contains("action=\"/dashboard/store/" + STORE_ID + "/delete?back=");
        assertThat(html).contains(">" + STORE_ID + "<");
    }

    @Test
    void deleteDialogClosedByDefault() {
        // when
        String html = render(page(row(StoreState.FULL, null, false, false, false, true, false, false)), null);

        // then
        assertThat(html).doesNotContainPattern("<dialog[^>]*id=\"store-delete-dialog\"[^>]*open=");
    }

    @Test
    void noticeAndEmptyState() {
        // given
        StoresListPage empty = new StoresListPage(StoresListQuery.of(null, "toys", null, null, null), List.of(), segments(), null,
                new StoresListPage.EmptyState("stores.list.empty.search", "toys", "stores.list.empty.showAll", "/dashboard/stores"));

        // when
        String html = render(empty, StoresNotice.warn("Already in that state"));

        // then
        assertThat(html).contains("cl-alert is-warn").contains("Already in that state");
        assertThat(html).contains("No store matches “toys”.").contains("Show all stores");
        assertThat(html).doesNotContain("<table");
    }

    @Test
    void searchShowsResultCountOnlyWhenThereIsAPhrase() {
        // given
        StoreRow row = row(StoreState.FULL, null, false, false, false, true, false, false);
        StoresListPage searched = new StoresListPage(StoresListQuery.of(null, "toys", null, null, null), List.of(row), segments(), null, null);

        // when
        String withPhrase = render(searched, null);
        String withoutPhrase = render(page(row), null);

        // then
        assertThat(withPhrase).contains("role=\"status\"").contains("Results: 1")
                .contains("<p class=\"cl-table-results cl-visually-hidden\" role=\"status\">");
        assertThat(withoutPhrase).doesNotContain("cl-table-results");
    }

    @Test
    void templateUsesNoLegacyMarkup() {
        // when
        String html = render(page(row(StoreState.FULL, null, false, false, false, true, false, false)), null);

        // then
        assertThat(html).doesNotContainPattern("class=\"(button|tag|table|title)[ \"]")
                .doesNotContain("style=").doesNotContain("onsubmit").doesNotContain("prompt(").doesNotContain("confirm(");
    }

    @Test
    void scriptAndStylesheetCoverTheDialogMarkup() throws Exception {
        // given
        String js = new String(getClass().getResourceAsStream("/static/js/stores.js").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String css = new String(getClass().getResourceAsStream("/static/css/commercelink.css").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

        // then
        assertThat(js).contains("store-delete-dialog").contains("cl:dialog-open").contains("data-store-delete-submit")
                .contains("data-store-id").contains("data-action");
        assertThat(css).contains(".cl-table.is-stores").contains(".cl-dialog-effects").contains(".cl-confirm-id")
                .contains(":is(.is-offers, .is-stores) .cl-table-actions");
    }
}
