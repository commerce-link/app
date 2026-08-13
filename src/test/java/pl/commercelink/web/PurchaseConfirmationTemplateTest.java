package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseConfirmationTemplateTest {

    private static final Path TEMPLATE =
            Path.of("src/main/resources/templates/deliveryPurchaseConfirmation.html");

    private String template() throws Exception {
        return Files.readString(TEMPLATE, StandardCharsets.UTF_8);
    }

    @Test
    void keepsTheElementIdsThePageScriptLooksUp() throws Exception {
        // when
        String html = template();

        // then
        assertThat(html).contains("id=\"address-modal\"");
        assertThat(html).contains("id=\"address-confirm\"");
        assertThat(html).contains("id=\"purchase-confirm-submit\"");
        assertThat(html).contains("data-address-cancel");
    }

    @Test
    void rendersTheAddressPickerOnlyInsideTheModal() throws Exception {
        // when
        String html = template();

        // then
        int modalStart = html.indexOf("id=\"address-modal\"");
        int pickerAt = html.indexOf("searchable-picker :: picker(");
        assertThat(pickerAt).isGreaterThan(modalStart);
        assertThat(html.indexOf("searchable-picker :: picker(", pickerAt + 1)).isEqualTo(-1);
    }

    @Test
    void labelsTheSubmitButtonDifferentlyWhenApprovalIsRequired() throws Exception {
        // when
        String html = template();

        // then
        assertThat(html).contains("deliveries.purchase.confirm.submitForApproval");
        assertThat(html).contains("requiresApproval");
    }
}
