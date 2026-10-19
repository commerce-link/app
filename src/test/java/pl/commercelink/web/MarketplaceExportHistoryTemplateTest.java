package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.messageresolver.IMessageResolver;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import pl.commercelink.marketplace.MarketplaceExportOfferSearch;
import pl.commercelink.marketplace.MarketplaceExportRunHeader;
import pl.commercelink.marketplace.MarketplaceExportRunId;
import pl.commercelink.marketplace.MarketplaceOfferSnapshot;
import pl.commercelink.web.settings.MarketplaceExportRunView;
import pl.commercelink.web.settings.MarketplaceExportSearchView;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceExportHistoryTemplateTest {

    private static final String RUN_ID = "8213415334_2026-08-13_01-31-05";
    private static final String LEGACY_RUN_ID = "2026-08-13_01-31-05";
    private static final String OLDER_RUN_ID = "8213589999_2026-08-11_01-00-00";

    @Test
    void rendersEveryColumnOfARunRow() {
        // given
        WebContext context = runDetailsContext(List.of(
                MarketplaceOfferSnapshot.published("pim-A", 3503L, 7L)
                        .rejected("VALIDATION_ERROR", "price out of range")), false);

        // when
        String html = templateEngine().process("store-marketplace-export-run", context);

        // then
        assertThat(html).contains("pim-A");
        assertThat(html).contains("3 503");
        assertThat(html).contains("VALIDATION_ERROR");
        assertThat(html).contains("price out of range");
        assertThat(html).contains("Odrzucona");
        assertThat(html).contains("Zakończony");
        assertThat(html).contains("/dashboard/store/marketplaces/exports/allegro/catalog-1/" + RUN_ID + "/file");
        assertThat(html).contains("Pobierz plik CSV");
        assertThat(html).doesNotContain("<pre>");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void showsTheReadableTimestampOfTheRunIdAsTheTitleInsteadOfTheCountdownPrefix() {
        // given
        WebContext context = runDetailsContext(List.of(
                MarketplaceOfferSnapshot.published("pim-A", 3503L, 7L)), false);

        // when
        String html = templateEngine().process("store-marketplace-export-run", context);

        // then
        assertThat(html).contains("Allegro · katalog Główny · 2026-08-13 01:31:05");
        assertThat(html).doesNotContain("8213415334_2026-08-13_01-31-05<");
    }

    @Test
    void showsTheFailedStatusForAFailedRun() {
        // given
        WebContext context = runDetailsContext(List.of(
                MarketplaceOfferSnapshot.exportAborted("java.lang.IllegalStateException: marketplace unavailable")), true);

        // when
        String html = templateEngine().process("store-marketplace-export-run", context);

        // then
        assertThat(html).contains("Nieudany");
        assertThat(html).contains("Eksport przerwany");
        assertThat(html).contains("marketplace unavailable");
        assertThat(html).contains("Przebieg nie zawiera ofert.");
        assertThat(html).doesNotContain("Zakończony");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void rendersEveryRowWhenTheRunHasMoreThanFiveHundred() {
        // given
        WebContext context = runDetailsContext(rows(1200), false);

        // when
        String html = templateEngine().process("store-marketplace-export-run", context);

        // then
        assertThat(countRows(html)).isEqualTo(1200);
        assertThat(html).contains("pim-1199");
        assertThat(html).doesNotContain("Pokazano");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void opensOnTheRejectionsAndOffersAFilterAndASearchAboveTheTable() {
        // given
        List<MarketplaceOfferSnapshot> rows = new ArrayList<>(rows(3));
        rows.add(MarketplaceOfferSnapshot.rejectedWithoutOffer("pim-X", "EAN_INVALID", "bad EAN"));
        rows.add(MarketplaceOfferSnapshot.removalPending("pim-Y", 999L, 2));
        WebContext context = runDetailsContext(rows, false);

        // when
        String html = templateEngine().process("store-marketplace-export-run", context);

        // then
        assertThat(html).contains("data-cl-filter-default=\"rejected\"");
        assertThat(html).contains("data-cl-filter=\"removal-pending\"");
        assertThat(html).contains("Zdejmowana · próba 2");
        assertThat(html).contains("data-cl-table-search");
        assertThat(html).contains("<th scope=\"col\"");
        assertThat(html).contains("/js/table-filter.js");
        assertThat(html).doesNotContain("??");
        assertThat(html.indexOf("data-cl-table-search")).isLessThan(html.indexOf("<table"));
    }

    @Test
    void opensOnEveryOfferWhenNothingWasRejected() {
        // when
        String html = templateEngine().process("store-marketplace-export-run", runDetailsContext(rows(3), false));

        // then
        assertThat(html).contains("data-cl-filter-default=\"all\"");
        assertThat(html).doesNotContain("data-cl-filter=\"rejected\"");
    }

    @Test
    void rendersRunHistoryTableWithTheFailureStatusOnTheHistoryPage() {
        // given
        WebContext context = historyContext(List.of(
                new MarketplaceExportRunHeader("allegro", "catalog-1", RUN_ID, true)));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("2026-08-13 01:31:05");
        assertThat(html).contains("/dashboard/store/marketplaces/exports/allegro/catalog-1/" + RUN_ID);
        assertThat(html).contains("Nieudany");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void showsTheReadablePartOfACountdownRunIdInTheRunsTable() {
        // given
        WebContext context = historyContext(List.of(
                new MarketplaceExportRunHeader("allegro", "catalog-1", RUN_ID, false)));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains(">2026-08-13 01:31:05</a>");
        assertThat(html).contains("/dashboard/store/marketplaces/exports/allegro/catalog-1/" + RUN_ID);
        assertThat(html).doesNotContain("??");
    }

    @Test
    void showsTheReadableTimestampOfARunIdWithoutACountdownPrefix() {
        // given
        WebContext context = historyContext(List.of(
                new MarketplaceExportRunHeader("allegro", "catalog-1", LEGACY_RUN_ID, false)));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains(">2026-08-13 01:31:05</a>");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void showsAnEmptyStateWhenThereAreNoRuns() {
        // given
        WebContext context = historyContext(List.of());

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("Brak zapisanych przebiegów eksportu.");
        assertThat(html).doesNotContain("<table");
        assertThat(html).doesNotContain("??");
    }

    /** A run file names its catalog by id; the page shows the catalog's name, and the id of a deleted catalog. */
    @Test
    void namesTheCatalogOfEachRunAndKeepsTheIdOfADeletedOne() {
        // given
        WebContext context = historyContext(List.of(
                new MarketplaceExportRunHeader("allegro", "catalog-1", RUN_ID, false),
                new MarketplaceExportRunHeader("allegro", "catalog-gone", RUN_ID, false)));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("Katalog: Główny").contains("Katalog: catalog-gone");
        assertThat(html).contains(">Zakończony<").doesNotContain("<table");
    }

    @Test
    void linksBackToTheMarketplacesPageFromTheHistoryPage() {
        // given
        WebContext context = historyContext(List.of());

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("href=\"/dashboard/store/marketplaces\"");
        assertThat(html).contains("<h1 class=\"cl-page-title\">Historia eksportu · Allegro</h1>");
    }

    @Test
    void linksBackToTheViewedStoreMarketplacesPageForASuperAdmin() {
        // given
        WebContext context = historyContext(List.of());
        context.setVariable("marketplacesHref", "/dashboard/store/store-1/marketplaces");

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("href=\"/dashboard/store/store-1/marketplaces\"");
    }

    @Test
    void tellsTheReaderHowManyRunsAreShownOnceTheLimitIsReached() {
        // given
        WebContext context = historyContext(headers(25));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("Pokazano 25 najnowszych przebiegów eksportu.");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void staysSilentAboutTheLimitWhenFewerRunsThanTheLimitExist() {
        // given
        WebContext context = historyContext(headers(24));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).doesNotContain("Pokazano");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void offersTheProductSearchAboveTheRunsOfTheMarketplace() {
        // given
        WebContext context = historyContext(headers(3));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("class=\"cl-card cl-search-card\"").contains("Znajdź produkt w eksportach");
        assertThat(html).contains("Przeszukamy do 500 ostatnich przebiegów i pokażemy 10 najnowszych wystąpień produktu.");
        assertThat(html).contains("action=\"/dashboard/store/marketplaces/exports/allegro\"");
        assertThat(html).contains("name=\"pimId\"");
        assertThat(html).contains("data-cl-submit-once").contains("/js/submit-once.js");
        assertThat(html.indexOf("Znajdź produkt w eksportach")).isLessThan(html.indexOf("Przebiegi eksportu ofert"));
        assertThat(html).doesNotContain("Wyniki dla").doesNotContain("cl-search-clear");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void hidesTheProductSearchWhenThereAreNoRuns() {
        // when
        String html = renderHistoryPage(historyContext(List.of()));

        // then
        assertThat(html).doesNotContain("Znajdź produkt w eksportach").doesNotContain("name=\"pimId\"");
    }

    @Test
    void listsEveryRunThatHasTheSearchedProductWithItsOutcomePriceAndReason() {
        // given
        WebContext context = historyContext(headers(3));
        context.setVariable("search", searchView("pim-A", List.of(
                hit(RUN_ID, false, MarketplaceOfferSnapshot.published("pim-A", 3503L, 7L)
                        .rejected("MISSING_PARAMETER", "no warranty")),
                hit(OLDER_RUN_ID, false, MarketplaceOfferSnapshot.published("pim-A", 2049L, 20L))), 20, 0));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("value=\"pim-A\"");
        assertThat(html).contains("id=\"marketplace-export-search-results\"").contains("Wyniki dla");
        assertThat(html).contains("href=\"/dashboard/store/marketplaces/exports/allegro/catalog-1/" + RUN_ID + "\"");
        assertThat(html).contains(">2026-08-13 01:31:05</a>").contains(">2026-08-11 01:00:00</a>");
        assertThat(html).contains("Główny");
        assertThat(html).contains("3 503").contains("2 049");
        assertThat(html).contains("Odrzucona").contains("Wystawiona");
        assertThat(html).contains("no warranty").contains("MISSING_PARAMETER");
        assertThat(html).contains("Wystąpienia: 2.").contains("Przejrzane przebiegi: 20, od 2026-08-11 01:00:00.");
        assertThat(html).doesNotContain("starszych przebiegów nie przeszukiwano");
        assertThat(html).doesNotContain("Nieudany");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void marksAHitFromAFailedRun() {
        // given
        WebContext context = historyContext(headers(3));
        context.setVariable("search", searchView("pim-A", List.of(
                hit(RUN_ID, true, MarketplaceOfferSnapshot.published("pim-A", 3503L, 7L))), 1, 0));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("<span class=\"cl-status is-warn\">Nieudany</span>");
    }

    @Test
    void tellsThatOlderRunsWereNotSearchedOnceTheHitLimitIsReached() {
        // given
        List<MarketplaceExportOfferSearch.Hit> hits = new ArrayList<>();
        for (MarketplaceExportRunHeader run : headers(10)) {
            hits.add(new MarketplaceExportOfferSearch.Hit(run, MarketplaceOfferSnapshot.published("pim-A", 1999L, 7L)));
        }
        WebContext context = historyContext(headers(25));
        context.setVariable("search", searchView("pim-A", hits, 10, 0));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("Pokazano 10 najnowszych wystąpień, starszych przebiegów nie przeszukiwano.");
        assertThat(html).doesNotContain("Wystąpienia:");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void tellsThatTheProductIsInNoSearchedRun() {
        // given
        WebContext context = historyContext(headers(3));
        context.setVariable("search", searchView("pim-X", List.of(), 500, 0));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("Produktu pim-X nie ma w żadnym z przejrzanych przebiegów eksportu na Allegro.");
        assertThat(html).contains("Przejrzane przebiegi: 500, od 2026-08-11 01:00:00.");
        assertThat(html).doesNotContain("<table").doesNotContain("Wystąpienia:");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void warnsAboutRunFilesThatCouldNotBeRead() {
        // given
        WebContext context = historyContext(headers(3));
        context.setVariable("search", searchView("pim-X", List.of(), 500, 2));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("Pominięte nieczytelne pliki przebiegów: 2.");
    }

    @Test
    void staysSilentAboutUnreadableRunFilesWhenEveryFileWasRead() {
        // given
        WebContext context = historyContext(headers(3));
        context.setVariable("search", searchView("pim-X", List.of(), 500, 0));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).doesNotContain("Pominięte nieczytelne pliki przebiegów");
    }

    @Test
    void clearsTheSearchedProductWithALinkToThePageWithoutIt() {
        // given
        WebContext context = historyContext(headers(3));
        context.setVariable("search", searchView("pim-X", List.of(), 500, 0));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("class=\"cl-search-clear\" href=\"/dashboard/store/marketplaces/exports/allegro\"");
    }

    private MarketplaceExportSearchView searchView(String pimId, List<MarketplaceExportOfferSearch.Hit> hits,
                                                   int searchedRuns, int unreadableRuns) {
        return MarketplaceExportSearchView.of(pimId,
                new MarketplaceExportOfferSearch(hits, searchedRuns, unreadableRuns, OLDER_RUN_ID), 10);
    }

    private MarketplaceExportOfferSearch.Hit hit(String runId, boolean failed, MarketplaceOfferSnapshot offer) {
        return new MarketplaceExportOfferSearch.Hit(
                new MarketplaceExportRunHeader("allegro", "catalog-1", runId, failed), offer);
    }

    private WebContext webContext() {
        JakartaServletWebApplication application =
                JakartaServletWebApplication.buildApplication(new MockServletContext());
        IWebExchange exchange = application.buildExchange(
                new MockHttpServletRequest(), new MockHttpServletResponse());
        return new WebContext(exchange, Locale.forLanguageTag("pl"));
    }

    private WebContext historyContext(List<MarketplaceExportRunHeader> runs) {
        WebContext context = webContext();
        context.setVariable("marketplace", "allegro");
        context.setVariable("storeId", "store-1");
        context.setVariable("exportRuns", runs);
        context.setVariable("runLimit", 25);
        context.setVariable("isSuperAdmin", false);
        context.setVariable("displayName", "Allegro");
        context.setVariable("catalogNames", Map.of("catalog-1", "Główny"));
        context.setVariable("marketplacesHref", "/dashboard/store/marketplaces");
        context.setVariable("historyHref", "/dashboard/store/marketplaces/exports/allegro");
        context.setVariable("searchRunLimit", 500);
        context.setVariable("searchHitLimit", 10);
        context.setVariable("backLabel", "Marketplaces");
        context.setVariable("pageTitle", "Historia eksportu · Allegro");
        return context;
    }

    private String renderHistoryPage(WebContext context) {
        return templateEngine().process("store-marketplace-export-history", context);
    }

    private List<MarketplaceExportRunHeader> headers(int count) {
        List<MarketplaceExportRunHeader> headers = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            headers.add(new MarketplaceExportRunHeader(
                    "allegro", "catalog-1", String.format("82134153%02d_2026-08-13_01-31-05", index), false));
        }
        return headers;
    }

    private WebContext runDetailsContext(List<MarketplaceOfferSnapshot> rows, boolean failed) {
        WebContext context = webContext();
        context.setVariable("runId", RUN_ID);
        context.setVariable("runTimestamp", MarketplaceExportRunId.readable(RUN_ID));
        context.setVariable("failed", failed);
        context.setVariable("view", MarketplaceExportRunView.of(rows));
        context.setVariable("fileHref", "/dashboard/store/marketplaces/exports/allegro/catalog-1/" + RUN_ID + "/file");
        context.setVariable("marketplace", "allegro");
        context.setVariable("catalogId", "catalog-1");
        context.setVariable("storeId", "store-1");
        context.setVariable("isSuperAdmin", false);
        context.setVariable("displayName", "Allegro");
        context.setVariable("catalogName", "Główny");
        context.setVariable("historyHref", "/dashboard/store/marketplaces/exports/allegro");
        context.setVariable("historyLabel", "Historia eksportu · Allegro");
        return context;
    }

    private List<MarketplaceOfferSnapshot> rows(int count) {
        List<MarketplaceOfferSnapshot> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            rows.add(MarketplaceOfferSnapshot.published("pim-" + index, 1999L, 7L));
        }
        return rows;
    }

    private int countRows(String html) {
        return html.split("<tr", -1).length - 2;
    }

    private TemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);

        TemplateEngine engine = new TemplateEngine();
        engine.setDialect(new SpringStandardDialect());
        engine.setTemplateResolver(resolver);
        engine.setMessageResolver(new PolishMessages());
        return engine;
    }

    private static class PolishMessages implements IMessageResolver {

        private final ResourceBundle messages =
                ResourceBundle.getBundle("messages", Locale.forLanguageTag("pl"));

        @Override
        public String getName() {
            return "polish";
        }

        @Override
        public Integer getOrder() {
            return 1;
        }

        @Override
        public String resolveMessage(ITemplateContext context, Class<?> origin, String key, Object[] parameters) {
            if (!messages.containsKey(key)) {
                return null;
            }
            String message = messages.getString(key);
            return parameters == null || parameters.length == 0
                    ? message
                    : new MessageFormat(message, Locale.forLanguageTag("pl")).format(parameters);
        }

        @Override
        public String createAbsentMessageRepresentation(ITemplateContext context, Class<?> origin, String key,
                                                        Object[] parameters) {
            return "??" + key + "??";
        }
    }
}
