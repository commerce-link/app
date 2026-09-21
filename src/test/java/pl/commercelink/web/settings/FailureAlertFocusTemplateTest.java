package pl.commercelink.web.settings;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An error that belongs to no field (a failed AWS call) is shown above long forms; async-form.js moves focus only to
 * [data-cl-error-summary], so without it the alert stayed off screen with no toast.
 */
class FailureAlertFocusTemplateTest {

    @ParameterizedTest
    @ValueSource(strings = {"store-marketplace", "store-supplier", "store-suppliers", "store-fulfilment"})
    void theErrorWithoutAFieldTakesFocusLikeTheErrorSummary(String template) throws Exception {
        // when
        String html = Files.readString(Path.of("src/main/resources/templates", template + ".html"), StandardCharsets.UTF_8);

        // then
        assertThat(html).contains("<div th:if=\"${failure != null}\" class=\"cl-alert is-bad\" data-cl-error-summary tabindex=\"-1\">");
    }
}
