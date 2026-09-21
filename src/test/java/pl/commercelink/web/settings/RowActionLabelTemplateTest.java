package pl.commercelink.web.settings;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Row actions of settings lists are named "visible text: record" (settings.list.*.for). A dialog question ("Delete
 * X?") or a different verb ("Edit X" on a "Complete" link) as the accessible name breaks WCAG 2.5.3 for voice control.
 */
class RowActionLabelTemplateTest {

    @ParameterizedTest
    @ValueSource(strings = {"store-warehouse", "store-payments", "rma-centers", "store-marketplaces", "store-suppliers",
            "store-shipping", "store-email-templates"})
    void rowActionsUseTheSharedVisibleTextLabels(String template) throws Exception {
        // when
        String html = Files.readString(Path.of("src/main/resources/templates", template + ".html"), StandardCharsets.UTF_8);

        // then
        assertThat(html).doesNotContainPattern("aria-label=[^,\"]*\\.(delete|disconnect)\\.title\\(");
        assertThat(html).doesNotContainPattern("aria-label=#\\{(?!settings\\.list\\.)[a-zA-Z.]+\\.edit\\.for\\(");
    }
}
