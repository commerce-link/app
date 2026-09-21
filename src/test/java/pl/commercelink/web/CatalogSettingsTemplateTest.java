package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
    void hasNoReadonlyIdFields() throws Exception {
        // when / then
        assertThat(page()).doesNotContain("storeId").doesNotContain("catalogId\"").doesNotContain("readonly");
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
