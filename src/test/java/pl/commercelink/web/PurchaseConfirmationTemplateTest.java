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
    private static final Path MODAL_FRAGMENT =
            Path.of("src/main/resources/templates/fragments/address-modal.html");
    private static final Pattern OPENING_TAG =
            Pattern.compile("<[a-zA-Z0-9:]+(?:\\s+[a-zA-Z0-9:_.-]+(?:=\"[^\"]*\")?)*\\s*/?>", Pattern.DOTALL);

    private String template() throws Exception {
        return Files.readString(TEMPLATE, StandardCharsets.UTF_8);
    }

    private String detailsTemplate() throws Exception {
        return Files.readString(DETAILS_TEMPLATE, StandardCharsets.UTF_8);
    }

    private String modalFragment() throws Exception {
        return Files.readString(MODAL_FRAGMENT, StandardCharsets.UTF_8);
    }

    @Test
    void rendersTheConfirmButtonDisabledUntilAnAddressIsChosen() throws Exception {
        // given
        String html = modalFragment();

        // when
        String confirmTag = openingTagOf(html, "id=\"address-confirm\"");

        // then
        assertThat(confirmTag).contains("disabled");
        assertThat(confirmTag).contains("selectedId");
        assertThat(template()).contains("refreshAddressConfirmState");
    }

    @Test
    void boundsTheOptionListHeightSoTheModalNeverGrowsWithTheAddressCount() throws Exception {
        // given
        String html = modalFragment();

        // then
        assertThat(html).contains("#address-options");
        assertThat(html).contains("max-height");
        assertThat(html).contains("overflow-y: auto");
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
        String fragmentHtml = modalFragment();
        String script = html.substring(html.indexOf("<script th:inline=\"none\">"), html.indexOf("</script>"));

        // then
        assertThat(fragmentHtml).contains("id=\"address-modal\"");
        assertThat(script).contains("'address-modal'");

        assertThat(fragmentHtml).contains("id=\"address-confirm\"");
        assertThat(script).contains("'address-confirm'");

        assertThat(html).contains("id=\"purchase-confirm-submit\"");
        assertThat(script).contains("'purchase-confirm-submit'");

        assertThat(fragmentHtml).contains("data-address-cancel");
        assertThat(script).contains("data-address-cancel");
    }

    @Test
    void offersTheAddressesAsAnAlwaysVisibleListInsideTheModal() throws Exception {
        // when
        String html = modalFragment();

        // then
        int modalStart = html.indexOf("id=\"address-modal\"");
        int optionsAt = html.indexOf("id=\"address-options\"");
        assertThat(optionsAt).isGreaterThan(modalStart);
        assertThat(html).contains("type=\"radio\" th:name=\"${fieldName}\"");
        assertThat(html).doesNotContain("searchable-picker :: picker(");
    }

    @Test
    void scrollsTheOptionListToThePreselectedAddressWhenTheModalOpens() throws Exception {
        // when
        String fragment = modalFragment();
        String html = template();

        // then
        assertThat(fragment).contains("function scrollAddressOptionsToSelection()");
        assertThat(fragment).contains("position: relative");
        assertThat(html).contains("addressModalScript");
        assertThat(html).contains("scrollAddressOptionsToSelection();");
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
    void guardsAddressPreselectionAgainstTwoNullsMatchingEachOther() throws Exception {
        // when
        String html = modalFragment();
        String radioTag = openingTagOf(html, "th:checked");
        int suggestedTagAt = html.indexOf("deliveries.approval.suggestedAddress.tag");
        String suggestedTagLine = html.substring(html.lastIndexOf('<', suggestedTagAt), html.indexOf('>', suggestedTagAt) + 1);

        // then
        assertThat(radioTag).contains("selectedId != null and option.value == selectedId");
        assertThat(suggestedTagLine).contains("selectedId != null and option.value == selectedId");
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

    @Test
    void rendersOrderOptionsInsideTheFormAndGatesSubmitOnThem() throws Exception {
        // when
        String html = template();

        // then
        assertThat(html).contains("fragments/order-options :: orderOptions(${orderOptions}, ${selectedOptions})");
        assertThat(html).contains("id=\"order-options-blocked\"");
        assertThat(html).contains("deliveries.options.error");
        String script = html.substring(html.indexOf("<script th:inline=\"none\">"), html.indexOf("</script>"));
        assertThat(script).contains("function refreshSubmitState()");
        int refreshStart = script.indexOf("function refreshSubmitState()");
        int refreshEnd = script.indexOf("}", refreshStart);
        assertThat(script.substring(refreshStart, refreshEnd)).contains("orderOptionsComplete()");
    }
}
