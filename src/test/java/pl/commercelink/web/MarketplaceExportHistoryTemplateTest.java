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

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    void limitsTheRowsTableAndAnnouncesTheRealTotalWhenThereAreTooManyRows() {
        // given
        WebContext context = runDetailsContext(rows(620), false);

        // when
        String html = templateEngine().process("store-marketplace-export-run", context);

        // then
        assertThat(countRows(html)).isEqualTo(500);
        assertThat(html).contains("Pokazano 500 z 620 wierszy");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void rendersEveryRowWithoutTheTruncationNoticeWhenBelowTheLimit() {
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
    void rendersRunHistoryTableWithTheFailureStatusOnTheMarketplacesPage() {
        // given
        WebContext context = webContext();
        context.setVariable("exportRuns", List.of(
                new MarketplaceExportRunHeader("allegro", "catalog-1", RUN_ID,
                        LocalDateTime.parse("2026-08-13T01:31:05"), true)));

        // when
        String html = renderRunsTable(context);

        // then
        assertThat(html).contains("2026-08-13 01:31:05");
        assertThat(html).contains("/dashboard/store/marketplaces/exports/allegro/catalog-1/" + RUN_ID);
        assertThat(html).contains("Nieudany");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void fallsBackToTheReadablePartOfACountdownRunIdWhenTheStoredAtIsMissing() {
        // given
        WebContext context = webContext();
        context.setVariable("exportRuns", List.of(
                new MarketplaceExportRunHeader("allegro", "catalog-1", RUN_ID, null, false)));

        // when
        String html = renderRunsTable(context);

        // then
        assertThat(html).contains(">2026-08-13 01:31:05</a>");
        assertThat(html).contains("/dashboard/store/marketplaces/exports/allegro/catalog-1/" + RUN_ID);
        assertThat(html).doesNotContain("??");
    }

    @Test
    void fallsBackToALegacyRunIdWhenTheStoredAtIsMissing() {
        // given
        WebContext context = webContext();
        context.setVariable("exportRuns", List.of(
                new MarketplaceExportRunHeader("allegro", "catalog-1", LEGACY_RUN_ID, null, false)));

        // when
        String html = renderRunsTable(context);

        // then
        assertThat(html).contains(">2026-08-13 01:31:05</a>");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void showsAnEmptyStateWhenThereAreNoRuns() {
        // given
        WebContext context = webContext();
        context.setVariable("exportRuns", List.of());

        // when
        String html = renderRunsTable(context);

        // then
        assertThat(html).contains("Brak zapisanych przebiegów eksportu.");
        assertThat(html).doesNotContain("<table");
    }

    private WebContext webContext() {
        JakartaServletWebApplication application =
                JakartaServletWebApplication.buildApplication(new MockServletContext());
        IWebExchange exchange = application.buildExchange(
                new MockHttpServletRequest(), new MockHttpServletResponse());
        return new WebContext(exchange, Locale.forLanguageTag("pl"));
    }

    private String renderRunsTable(WebContext context) {
        context.setVariable("isSuperAdmin", false);
        String template = """
                <div th:with="basePath=${isSuperAdmin} ? '/dashboard/store/x' : '/dashboard/store'">
                  <table class="table" th:if="${!exportRuns.isEmpty()}">
                    <tbody>
                    <tr th:each="run : ${exportRuns}">
                      <td><a th:href="@{${basePath + '/marketplaces/exports/' + run.marketplace() + '/' + run.catalogId() + '/' + run.runId()}}"
                             th:text="${T(pl.commercelink.marketplace.MarketplaceExportRunId).readable(run.runId())}"></a></td>
                      <td>
                        <span th:unless="${run.failed()}" th:text="#{store.marketplaces.exports.status.succeeded}"></span>
                        <span th:if="${run.failed()}" th:text="#{store.marketplaces.exports.status.failed}"></span>
                      </td>
                    </tr>
                    </tbody>
                  </table>
                  <p th:if="${exportRuns.isEmpty()}" th:text="#{store.marketplaces.exports.empty}"></p>
                </div>
                """;
        return stringTemplateEngine().process(template, context);
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
        context.setVariable("rawTooLarge", false);
        context.setVariable("raw", null);
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

    private TemplateEngine stringTemplateEngine() {
        TemplateEngine engine = new TemplateEngine();
        engine.setDialect(new SpringStandardDialect());
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
