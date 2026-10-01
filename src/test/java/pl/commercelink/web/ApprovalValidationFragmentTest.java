package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.PurchaseValidation;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalValidationFragmentTest {

    private static final String FRAGMENT = "<div th:replace=\"~{fragments/approval-validation :: validationResult}\"></div>";

    @Test
    void marksTheMissingQuantityAndTheVerdictForTheApprovalScript() {
        // given
        PurchaseValidation validation = new PurchaseValidation("Acme", "ref-1", "PLN", 1198.0, false, List.of(
                new PurchaseValidation.Line("AMD Ryzen 7 9800X3D", "sku", "5901234123457", "MFN", 2, 1, 579.5, 599.0)));

        // when
        String html = SettingsTemplateRenderer.render(FRAGMENT, Map.of("validation", validation));

        // then
        assertThat(html).contains("data-fully-available=\"false\"").contains("Brakuje: 1")
                .contains("AMD Ryzen 7 9800X3D").contains("PLN").doesNotContain("??");
    }

    @Test
    void offersTheRetryLinkTheApprovalScriptHandles() {
        // when
        String html = SettingsTemplateRenderer.render(FRAGMENT, Map.of("validationError", "timeout"));

        // then
        assertThat(html).contains("timeout").contains("onclick=\"loadValidation()\"").contains("Spróbuj ponownie")
                .doesNotContain("??");
    }
}
