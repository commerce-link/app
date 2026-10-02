package pl.commercelink.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsSubpageHeaderTemplateTest {

    private String template(String name) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/" + name + ".html"), StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"store-company-details", "store-branding", "store-invoicing", "store-payments",
            "store-marketplaces", "store-categories", "store-report", "store-fulfilment", "store-suppliers", "store-warehouse",
            "store-shipping", "store-rma", "rma-centers", "store-notification", "store-email-templates"})
    void everySettingsSubpageUsesTheSharedHeaderInsideThePageWrapper(String name) throws Exception {
        // when
        String html = template(name);

        // then
        assertThat(html).containsOnlyOnce("fragments/settings-header :: header(");
        assertThat(html).contains("<section class=\"cl-page\">").contains("class=\"cl-page-body\"");
        assertThat(html).doesNotContain("<h1").doesNotContain("class=\"section\"").doesNotContain("class=\"container\"");
    }
}
