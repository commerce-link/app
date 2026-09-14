package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import pl.commercelink.marketplace.MarketplaceIntegrationView;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceSectionRenderingTest {

    private static final String WRAPPER = "<div th:replace=\"~{fragments/marketplace-section :: marketplaceSection}\"></div>";

    private static WebContext webContext() {
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(new MockServletContext());
        return new WebContext(application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse()),
                Locale.ENGLISH);
    }

    private static WebContext context(List<MarketplaceIntegrationView> rows) {
        WebContext context = webContext();
        context.setVariable("sectionRows", rows);
        context.setVariable("sectionAddDisabled", false);
        context.setVariable("sectionSuccessMessage", "Marketplace Empik settings saved.");
        context.setVariable("sectionMarketplacesWithStoredConfig", "Empik");
        context.setVariable("sectionBasePath", "/dashboard/store");
        return context;
    }

    @Test
    void rendersTheSectionExactlyAsStoreMarketplaceControllerPublishesIt() {
        // given
        MarketplaceIntegrationView empik = new MarketplaceIntegrationView(
                "Empik", "EmpikPlace", true, false, LocalDateTime.of(2026, 9, 14, 8, 30), "0/15 * * * ? *");
        MarketplaceIntegrationView allegro = new MarketplaceIntegrationView(
                "Allegro", "Allegro.pl", false, true, null, null);

        // when
        String html = EnglishFragmentTemplateEngine.create().process(WRAPPER, context(List.of(empik, allegro)));

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("data-success-message=\"Marketplace Empik settings saved.\"");
        assertThat(html).contains("data-marketplaces-with-stored-config=\"Empik\"");
        assertThat(html).contains("id=\"marketplace-add-button\"");
        assertThat(html).contains("Orders import schedule");
        assertThat(html).contains("EmpikPlace");
        assertThat(html).contains("14.09.2026 08:30");
        assertThat(html).contains("Every 15 min");
        assertThat(html).contains("title=\"0/15 * * * ? *\"");
        assertThat(html).contains("Default — every 10 min");
        assertThat(html).doesNotContain("once a day");
        assertThat(html).contains("data-configure-marketplace=\"Empik\"");
        assertThat(html).contains("data-orders-import-schedule=\"0/15 * * * ? *\"");
        assertThat(html).contains("class=\"tag is-warning\"");
        assertThat(html).contains("data-provider=\"Allegro\"");
        assertThat(html).contains("href=\"/dashboard/store/marketplaces/exports/Empik\"");
        assertThat(html).contains("data-identity=\"Empik\"");
    }

    @Test
    void anEmptyListShowsTheEmptyStateAndKeepsTheAddButton() {
        // when
        String html = EnglishFragmentTemplateEngine.create().process(WRAPPER, context(List.of()));

        // then
        assertThat(html).doesNotContain("<table");
        assertThat(html).contains("No marketplaces connected yet.");
        assertThat(html).contains("id=\"marketplace-add-button\"");
    }

    @Test
    void theErrorFragmentCarriesTheMessageAsADangerNotification() {
        // given
        WebContext context = webContext();
        context.setVariable("errorMessage", "Marketplace Empik requires field API Key.");

        // when
        String html = EnglishFragmentTemplateEngine.create().process(
                "<div th:replace=\"~{fragments/marketplace-section :: sectionError}\"></div>", context);

        // then
        assertThat(html).contains("notification is-danger");
        assertThat(html).contains("Marketplace Empik requires field API Key.");
    }
}
