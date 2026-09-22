package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.web.dtos.CatalogSettingsForm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSettingsTemplateTest {

    private static String page() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/catalog/catalog-settings.html"), StandardCharsets.UTF_8);
    }

    @Test
    void savesWithoutReloadAndReturnsToTheCatalog() throws Exception {
        // when / then
        assertThat(page()).contains("th:fragment=\"settingsForm\"").contains("id=\"catalog-settings-form\"").contains("data-cl-async")
                .contains("data-cl-redirect=${redirectTo}").contains("@{/js/async-form.js}").contains("errorSummary('catalog-settings-errors'")
                .contains("schedule-field :: input('pricelistSchedule'").contains("data-cl-schedule-auto");
    }

    @Test
    void deleteIsOfferedOnlyForAnUnprotectedExistingCatalog() throws Exception {
        // when / then
        assertThat(page()).contains("th:if=\"${deleteHref != null}\"").contains("data-cl-confirm").contains("fragments/confirm-dialog :: dialog")
                .contains("#{catalog.protect.hint}").doesNotContain("confirmDelete(");
    }

    @Test
    void showsTheOutcomeOfACatalogActionInThePageBody() throws Exception {
        // when / then
        assertThat(page()).contains("settings-form :: savedAlert").contains("th:if=\"${catalogError}\"")
                .contains("class=\"cl-alert is-bad\" role=\"status\"").contains("cl-alert-text")
                .doesNotContain("errorMessage").doesNotContain("notification is-");
    }

    @Test
    void hasNoReadonlyIdFields() throws Exception {
        // when / then
        assertThat(page()).doesNotContain("storeId").doesNotContain("catalogId\"").doesNotContain("readonly");
    }

    /** The page as the controller renders it for an unprotected catalog, so the delete link is on it. */
    private static String rendered() {
        CatalogSettingsForm form = new CatalogSettingsForm();
        form.setName("Parts");
        Context context = new Context();
        context.setVariables(Map.of(
                "form", form,
                "errors", Map.of(),
                "existing", true,
                "deleteHref", "/dashboard/catalogs/c1/delete",
                "formAction", "/dashboard/catalogs/c1/settings",
                "backHref", "/dashboard/catalogs/c1",
                "backLabel", "Parts",
                "pageTitle", "Catalog settings",
                "scheduleMinIntervalMinutes", 5));
        context.setVariable("categoriesCount", 2);
        context.setVariable("productsCount", 7);
        context.setVariable("catalogError", null);
        context.setVariable("catalogId", "c1");
        context.setVariable("redirectTo", null);
        return EnglishFragmentTemplateEngine.create().process("catalog/catalog-settings", context);
    }

    private static int occurrences(String html, String needle) {
        return html.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void theDeleteActionIsRenderedOnceInsideTheHeader() {
        // when
        String html = rendered();

        // then
        assertThat(occurrences(html, "/dashboard/catalogs/c1/delete")).isEqualTo(1);
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(html.indexOf("cl-page-actions")).isLessThan(html.indexOf("/dashboard/catalogs/c1/delete"));
        assertThat(html).doesNotContain("??");
    }

    /** Support is given a catalog id over the phone; the page it is on must show it, as text rather than as a field. */
    @Test
    void theCatalogIdIsOnThePageAsPlainText() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("ID: c1").doesNotContain("readonly");
    }

    @Test
    void theCatalogIdLineIsLeftOutWhileTheCatalogDoesNotExistYet() throws Exception {
        // when / then
        assertThat(page()).contains("th:if=\"${catalogId != null}\"");
    }

    @Test
    void theCatalogDefaultTextResolvesInTheBuilder() {
        // when
        String html = EnglishFragmentTemplateEngine.create().process(
                "<div th:replace=\"~{fragments/schedule-field :: input('pricelistSchedule', '', 5, "
                        + "#{catalog.pricelist.schedule.summary.default}, 'minutes,hours,days')}\"></div>", new Context());

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("data-default-text=\"Default — once a day, at a random hour at night\"");
        assertThat(html).contains("data-units=\"minutes,hours,days\"");
    }
}
