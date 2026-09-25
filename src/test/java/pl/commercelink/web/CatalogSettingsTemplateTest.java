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
                .doesNotContain("confirmDelete(");
    }

    @Test
    void saysNothingBelowTheProtectionAboutHowToDelete() throws Exception {
        // when / then
        assertThat(page()).doesNotContain("catalog.protect.hint");
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
    void theCatalogIdStandsUnderThePageTitleAsPlainText() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("ID: c1").doesNotContain("readonly");
        // one placement across the catalog screens: in the header, after the title block, before the form
        assertThat(html.indexOf("cl-page-title")).isLessThan(html.indexOf("ID: c1"));
        assertThat(html.indexOf("ID: c1")).isLessThan(html.indexOf("<form"));
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

    /** RF-5: a new catalog's id travels with the form, so the same form sent twice names the same catalog. */
    @Test
    void theNewCatalogFormPostsTheIdItWasGiven() {
        // given
        CatalogSettingsForm form = CatalogSettingsForm.forNewCatalog();
        form.setNewCatalogId("k3y0000001");
        Context context = new Context();
        context.setVariables(Map.of("form", form, "errors", Map.of(), "existing", false,
                "formAction", "/dashboard/catalogs/new", "backHref", "/dashboard/catalogs", "backLabel", "Catalogs",
                "pageTitle", "New catalog", "scheduleMinIntervalMinutes", 5));
        context.setVariable("deleteHref", null);
        context.setVariable("catalogId", null);
        context.setVariable("redirectTo", null);

        // when
        String html = EnglishFragmentTemplateEngine.create().process("catalog/catalog-settings", context);

        // then
        assertThat(html).contains("<input type=\"hidden\" name=\"newCatalogId\" value=\"k3y0000001\"");
    }

    /** RF-27: a failed save is the form's summary alert with its message, and no field is marked. */
    @Test
    void aFailedSaveRendersAnAlertWithoutAFieldError() {
        // given
        CatalogSettingsForm form = new CatalogSettingsForm();
        form.setName("Parts");
        Context context = new Context();
        context.setVariables(Map.of("form", form, "errors", Map.of(), "existing", true,
                "formAction", "/dashboard/catalogs/c1/settings", "backHref", "/dashboard/catalogs/c1", "backLabel", "Parts",
                "pageTitle", "Catalog settings", "scheduleMinIntervalMinutes", 5, "formError", "catalog.save.error.failed"));
        context.setVariable("deleteHref", null);
        context.setVariable("catalogId", "c1");
        context.setVariable("redirectTo", null);

        // when
        String html = EnglishFragmentTemplateEngine.create().process("catalog/catalog-settings", context);

        // then
        assertThat(html).containsPattern("id=\"catalog-settings-errors\" class=\"cl-alert is-bad\"[^>]*data-cl-error-summary")
                .contains("Failed to save the catalog. Please try again.")
                .doesNotContain("pricelistSchedule-error").doesNotContain("is-invalid")
                .contains("document.getElementById('catalog-settings-errors').focus()");
    }

    @Test
    void anExistingCatalogPostsNoNewId() {
        // when / then
        assertThat(rendered()).doesNotContain("newCatalogId");
    }
}
