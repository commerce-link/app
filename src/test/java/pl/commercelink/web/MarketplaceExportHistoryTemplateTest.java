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
import pl.commercelink.marketplace.MarketplaceExportRun;
import pl.commercelink.marketplace.MarketplaceExportRunDocument;
import pl.commercelink.marketplace.MarketplaceExportRunHeader;
import pl.commercelink.marketplace.MarketplaceExportSkipReason;
import pl.commercelink.marketplace.MarketplaceExportThresholds;
import pl.commercelink.marketplace.MarketplaceOfferSnapshot;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceExportHistoryTemplateTest {

    private static final String RUN_ID = "2026-08-13_01-31-05";

    @Test
    void rendersRunDetailsWithLocalizedReasonAndThresholdNumbers() {
        // given
        WebContext context = webContext();
        context.setVariable("run", failedDocument());
        context.setVariable("reasonLabels", Map.of(
                MarketplaceExportSkipReason.QUANTITY_ZEROED_BELOW_DISTRIBUTOR_THRESHOLDS.name(),
                "Ilosc wyzerowana - ponizej progow dostawcow"));
        context.setVariable("marketplace", "allegro");
        context.setVariable("catalogId", "catalog-1");
        context.setVariable("runId", RUN_ID);
        context.setVariable("storeId", "store-1");
        context.setVariable("isSuperAdmin", false);
        context.setVariable("rawTooLarge", false);
        context.setVariable("raw", "{\"runId\":\"" + RUN_ID + "\"}");

        // when
        String html = templateEngine().process("store-marketplace-export-run", context);

        // then
        assertThat(html).contains("Ilosc wyzerowana - ponizej progow dostawcow");
        assertThat(html).contains("Laptopy");
        assertThat(html).contains("Name-B");
        assertThat(html).contains("marketplace unavailable");
        assertThat(html).contains("/dashboard/store/marketplaces/exports/allegro/catalog-1/" + RUN_ID + "/file");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void limitsTheExcludedTableAndAnnouncesTheRealTotalWhenThereAreTooManyRows() {
        // given
        WebContext context = runDetailsContext(documentWithExcludedProducts(620));

        // when
        String html = templateEngine().process("store-marketplace-export-run", context);

        // then
        assertThat(countRows(html)).isEqualTo(500);
        assertThat(html).contains("Pokazano 500 z 620 pozycji");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void rendersEveryExcludedRowWithoutTheTruncationNoticeWhenBelowTheLimit() {
        // given
        WebContext context = runDetailsContext(documentWithExcludedProducts(12));

        // when
        String html = templateEngine().process("store-marketplace-export-run", context);

        // then
        assertThat(countRows(html)).isEqualTo(12);
        assertThat(html).doesNotContain("Pokazano");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void rendersRunHistoryTableOnTheMarketplacesPage() {
        // given
        WebContext context = webContext();
        context.setVariable("exportRuns", List.of(new MarketplaceExportRunHeader(
                "allegro", "catalog-1", RUN_ID, LocalDateTime.parse("2026-08-13T01:31:05"))));

        // when
        String html = renderRunsTable(context);

        // then
        assertThat(html).contains("2026-08-13 01:31:05");
        assertThat(html).contains("/dashboard/store/marketplaces/exports/allegro/catalog-1/" + RUN_ID);
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
                             th:text="${run.storedAt() != null} ? ${#temporals.format(run.storedAt(), 'yyyy-MM-dd HH:mm:ss')} : ${run.runId()}"></a></td>
                    </tr>
                    </tbody>
                  </table>
                  <p th:if="${exportRuns.isEmpty()}" th:text="#{store.marketplaces.exports.empty}"></p>
                </div>
                """;
        return stringTemplateEngine().process(template, context);
    }

    private WebContext runDetailsContext(MarketplaceExportRunDocument document) {
        WebContext context = webContext();
        context.setVariable("run", document);
        context.setVariable("reasonLabels", Map.of());
        context.setVariable("marketplace", "allegro");
        context.setVariable("catalogId", "catalog-1");
        context.setVariable("runId", RUN_ID);
        context.setVariable("storeId", "store-1");
        context.setVariable("isSuperAdmin", false);
        context.setVariable("rawTooLarge", false);
        context.setVariable("raw", null);
        return context;
    }

    private int countRows(String html) {
        return html.split("<tr", -1).length - 2;
    }

    private MarketplaceExportRunDocument documentWithExcludedProducts(int count) {
        MarketplaceExportRun run = new MarketplaceExportRun("store-1", "allegro", "catalog-1", "pricelist-1");
        run.providerCalled(true);
        for (int index = 0; index < count; index++) {
            run.excludeProduct(category(), product("pim-" + index),
                    MarketplaceExportSkipReason.QUANTITY_ZEROED_BELOW_DISTRIBUTOR_THRESHOLDS,
                    MarketplaceExportThresholds.distributors(3L, 3, 1L, 2, 2));
        }
        return run.toDocument(RUN_ID, Instant.parse("2026-08-13T01:31:05Z"));
    }

    private MarketplaceExportRunDocument failedDocument() {
        MarketplaceExportRun run = new MarketplaceExportRun("store-1", "allegro", "catalog-1", "pricelist-1");
        run.providerCalled(true);
        run.offers(List.of(MarketplaceOfferSnapshot.published("pim-A", 3503L, 0L,
                MarketplaceExportSkipReason.QUANTITY_ZEROED_BELOW_DISTRIBUTOR_THRESHOLDS.name())));
        run.excludeProduct(category(), product(),
                MarketplaceExportSkipReason.QUANTITY_ZEROED_BELOW_DISTRIBUTOR_THRESHOLDS,
                MarketplaceExportThresholds.distributors(3L, 3, 1L, 2, 2));
        run.failed(new IllegalStateException("marketplace unavailable"));
        return run.toDocument(RUN_ID, Instant.parse("2026-08-13T01:31:05Z"));
    }

    private CategoryDefinition category() {
        CategoryDefinition category = new CategoryDefinition();
        category.setCategoryId("category-1");
        category.setName("Laptopy");
        return category;
    }

    private Product product() {
        return product("pim-B");
    }

    private Product product(String pimId) {
        return new Product("category-1", pimId, "EAN-B", "MFN-B", "Brand", "Label", "Name-B", "default");
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
