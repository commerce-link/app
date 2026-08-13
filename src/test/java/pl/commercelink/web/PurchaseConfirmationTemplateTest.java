package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseConfirmationTemplateTest {

    private static final Path TEMPLATE =
            Path.of("src/main/resources/templates/deliveryPurchaseConfirmation.html");
    private static final Path DETAILS_TEMPLATE =
            Path.of("src/main/resources/templates/deliveryDetails.html");
    private static final Pattern OPENING_TAG = Pattern.compile("<[a-zA-Z0-9:]+\\s[^>]*?>", Pattern.DOTALL);

    private String template() throws Exception {
        return Files.readString(TEMPLATE, StandardCharsets.UTF_8);
    }

    private String detailsTemplate() throws Exception {
        return Files.readString(DETAILS_TEMPLATE, StandardCharsets.UTF_8);
    }

    @Test
    void rendersTheConfirmButtonDisabledUntilAnAddressIsChosen() throws Exception {
        // given
        String html = template();

        // when
        String confirmTag = openingTagOf(html, "id=\"address-confirm\"");

        // then
        assertThat(confirmTag).contains("disabled");
        assertThat(confirmTag).contains("deliveryAddressId");
        assertThat(html).contains("refreshAddressConfirmState");
    }

    @Test
    void givesTheAddressModalEnoughRoomForTheOptionList() throws Exception {
        // given
        String html = template();

        // then
        assertThat(html).contains("#address-modal .modal-card-body");
        assertThat(html).contains("min-height");
    }

    private String openingTagOf(String html, String marker) {
        Matcher matcher = OPENING_TAG.matcher(html);
        while (matcher.find()) {
            if (matcher.group().contains(marker)) {
                return matcher.group();
            }
        }
        return "";
    }

    private boolean hasElementWithBothThIfAndThReplace(String html) {
        Matcher matcher = OPENING_TAG.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            if (tag.contains("th:if") && tag.contains("th:replace")) {
                return true;
            }
        }
        return false;
    }

    @Test
    void keepsTheElementIdsThePageScriptLooksUp() throws Exception {
        // when
        String html = template();
        String script = html.substring(html.indexOf("<script th:inline=\"none\">"), html.indexOf("</script>"));

        // then
        assertThat(html).contains("id=\"address-modal\"");
        assertThat(script).contains("'address-modal'");

        assertThat(html).contains("id=\"address-confirm\"");
        assertThat(script).contains("'address-confirm'");

        assertThat(html).contains("id=\"purchase-confirm-submit\"");
        assertThat(script).contains("'purchase-confirm-submit'");

        assertThat(html).contains("data-address-cancel");
        assertThat(script).contains("data-address-cancel");
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

    @Test
    void pickerScriptGuardIsNeverCombinedWithThReplaceOnTheSameElement() throws Exception {
        // when
        String purchaseConfirmationHtml = template();
        String detailsHtml = detailsTemplate();

        // then
        assertThat(hasElementWithBothThIfAndThReplace(purchaseConfirmationHtml)).isFalse();
        assertThat(hasElementWithBothThIfAndThReplace(detailsHtml)).isFalse();
    }
}
