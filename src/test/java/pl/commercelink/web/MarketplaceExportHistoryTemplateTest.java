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
import pl.commercelink.marketplace.MarketplaceExportRunHeader;
import pl.commercelink.marketplace.MarketplaceExportRunId;
import pl.commercelink.marketplace.MarketplaceOfferSnapshot;
import pl.commercelink.web.dtos.ConnectedIntegration;

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
        assertThat(html).contains("3503");
        assertThat(html).contains("VALIDATION_ERROR");
        assertThat(html).contains("price out of range");
        assertThat(html).contains("Zakończony");
        assertThat(html).contains("/dashboard/store/marketplaces/exports/allegro/catalog-1/" + RUN_ID + "/file");
        assertThat(html).contains("Otwórz surowy plik");
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
        assertThat(html).contains(">2026-08-13 01:31:05<");
        assertThat(html).doesNotContain(">8213415334_2026-08-13_01-31-05<");
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
        assertThat(html).contains("marketplace unavailable");
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
    void rendersEveryRowOfASmallRun() {
        // given
        WebContext context = runDetailsContext(rows(12), false);

        // when
        String html = templateEngine().process("store-marketplace-export-run", context);

        // then
        assertThat(countRows(html)).isEqualTo(12);
        assertThat(html).doesNotContain("Pokazano");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void rendersTheProductIdSearchBoxAboveTheRowsTable() {
        // given
        WebContext context = runDetailsContext(rows(3), false);

        // when
        String html = templateEngine().process("store-marketplace-export-run", context);

        // then
        assertThat(html).contains("id=\"exportRowsSearch\"");
        assertThat(html).contains("class=\"input is-small\"");
        assertThat(html).contains("Szukaj po PIM ID produktu");
        assertThat(html).contains("class=\"export-row\"");
        assertThat(html).contains("class=\"export-row-pim-id\"");
        assertThat(html).contains("getElementById('exportRowsSearch')");
        assertThat(html).contains("is-hidden");
        assertThat(html).doesNotContain("??");
        assertThat(html.indexOf("exportRowsSearch")).isLessThan(html.indexOf("<table"));
    }

    @Test
    void linksToTheMarketplaceExportHistoryPageNextToTheDisconnectButtonOfTheMarketplacePanel() {
        // when
        String html = renderIntegrationPanel("marketplace", false);

        // then
        assertThat(html).contains("Zobacz historię eksportu");
        assertThat(html).contains("href=\"/dashboard/store/marketplaces/exports/allegro\"");
        assertThat(html).doesNotContain("#marketplace-export-history");
        assertThat(html).contains("Rozłącz");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void linksToTheExportHistoryOfTheViewedStoreForASuperAdmin() {
        // when
        String html = renderIntegrationPanel("marketplace", true);

        // then
        assertThat(html).contains("href=\"/dashboard/store/store-1/marketplaces/exports/allegro\"");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void hidesTheExportHistoryLinkOnPanelsOtherThanMarketplace() {
        // when / then
        assertThat(renderIntegrationPanel("shipping", false)).doesNotContain("Zobacz historię eksportu");
        assertThat(renderIntegrationPanel("payments", false)).doesNotContain("Zobacz historię eksportu");
        assertThat(renderIntegrationPanel("invoicing", false)).doesNotContain("Zobacz historię eksportu");
        assertThat(renderIntegrationPanel("printing", false)).doesNotContain("Zobacz historię eksportu");
    }

    @Test
    void marketplacesPageNoLongerRendersTheExportHistoryTable() {
        // when
        String html = renderMarketplacesPage();

        // then
        assertThat(html).doesNotContain("id=\"marketplace-export-history\"");
        assertThat(html).doesNotContain("Historia eksportów ofert");
        assertThat(html).doesNotContain("Brak zapisanych przebiegów eksportu.");
        assertThat(html).contains("Zobacz historię eksportu");
        assertThat(html).doesNotContain("??");
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

    @Test
    void dropsTheMarketplaceColumnAndKeepsTheCatalogOneOnTheHistoryPage() {
        // given
        WebContext context = historyContext(List.of(
                new MarketplaceExportRunHeader("allegro", "catalog-1", RUN_ID, false)));

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("Data przebiegu");
        assertThat(html).contains("Katalog");
        assertThat(html).contains("Status");
        assertThat(html).doesNotContain("<th>Marketplace</th>");
        assertThat(countHeaderCells(html)).isEqualTo(3);
    }

    @Test
    void linksBackToTheMarketplacesPageFromTheHistoryPage() {
        // given
        WebContext context = historyContext(List.of());

        // when
        String html = renderHistoryPage(context);

        // then
        assertThat(html).contains("href=\"/dashboard/store/marketplaces\"");
        assertThat(html).contains("Powrót");
    }

    @Test
    void linksBackToTheViewedStoreMarketplacesPageForASuperAdmin() {
        // given
        WebContext context = historyContext(List.of());
        context.setVariable("isSuperAdmin", true);

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
        return context;
    }

    private String renderHistoryPage(WebContext context) {
        return templateEngine().process("store-marketplace-export-history", context);
    }

    private String renderMarketplacesPage() {
        WebContext context = webContext();
        context.setVariable("connectedIntegrations", List.of(new ConnectedIntegration("allegro", true)));
        context.setVariable("availableProviders", List.of());
        context.setVariable("selectedProviderName", "allegro");
        context.setVariable("form", new StubStoreForm());
        context.setVariable("isSuperAdmin", false);
        return templateEngine().process("store-marketplaces", context);
    }

    private List<MarketplaceExportRunHeader> headers(int count) {
        List<MarketplaceExportRunHeader> headers = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            headers.add(new MarketplaceExportRunHeader(
                    "allegro", "catalog-1", String.format("82134153%02d_2026-08-13_01-31-05", index), false));
        }
        return headers;
    }

    private int countHeaderCells(String html) {
        return html.split("<th>", -1).length - 1;
    }

    private WebContext runDetailsContext(List<MarketplaceOfferSnapshot> rows, boolean failed) {
        WebContext context = webContext();
        context.setVariable("runId", RUN_ID);
        context.setVariable("runTimestamp", MarketplaceExportRunId.readable(RUN_ID));
        context.setVariable("failed", failed);
        context.setVariable("rows", rows);
        context.setVariable("marketplace", "allegro");
        context.setVariable("catalogId", "catalog-1");
        context.setVariable("storeId", "store-1");
        context.setVariable("isSuperAdmin", false);
        return context;
    }

    private String renderIntegrationPanel(String providerType, boolean superAdmin) {
        WebContext context = webContext();
        context.setVariable("isSuperAdmin", superAdmin);
        context.setVariable("connectedIntegrations", List.of(new ConnectedIntegration("allegro", true)));
        context.setVariable("providers", List.of());
        context.setVariable("selectedProviderName", "allegro");
        context.setVariable("selectLabel", "Marketplace");
        context.setVariable("providerConfiguration", Map.of());
        context.setVariable("providerType", providerType);
        context.setVariable("storeId", "store-1");
        context.setVariable("showDefault", false);
        return templateEngine().process("fragments/integration-panel", context);
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

    public static class StubStoreForm {

        public Map<String, String> getProviderConfiguration() {
            return Map.of();
        }

        public StubStore getStore() {
            return new StubStore();
        }
    }

    public static class StubStore {

        public String getStoreId() {
            return "store-1";
        }
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
