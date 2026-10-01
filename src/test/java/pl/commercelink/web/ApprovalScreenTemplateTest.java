package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalScreenTemplateTest {

    private static final Path APPROVAL = Path.of("src/main/resources/templates/deliveryApproval.html");
    private static final Pattern OPENING_TAG =
            Pattern.compile("<[a-zA-Z0-9:]+(?:\\s+[a-zA-Z0-9:_.-]+(?:=\"[^\"]*\")?)*\\s*/?>", Pattern.DOTALL);

    private String approval() throws Exception {
        return Files.readString(APPROVAL, StandardCharsets.UTF_8);
    }

    @Test
    void offersOnlyTheRealisationOutcomeOnTheApprovalScreen() throws Exception {
        // when
        String html = approval();

        // then
        assertThat(html).contains("deliveries.approval.realize");
        assertThat(html).doesNotContain("deliveries.approval.reject");
        assertThat(html).doesNotContain("name=\"reason\"");
        assertThat(html).doesNotContain("/reject");
    }

    @Test
    void reusesTheSharedAddressModalFragment() throws Exception {
        // when
        String html = approval();

        // then
        assertThat(html).contains("fragments/address-modal :: addressModal(");
        assertThat(html).doesNotContain("searchable-picker :: picker(");
    }

    @Test
    void keepsTheApproveButtonDisabledUntilTheChecksPass() throws Exception {
        // when
        String html = approval();
        String approveTag = openingTagOf(html, "id=\"approval-approve-button\"");

        // then
        assertThat(approveTag).contains("disabled");
        assertThat(html).contains("refreshApprovalSubmitState");
        assertThat(html).contains("approvalValidationPassed");
        assertThat(html).contains("approve.disabled = addressBlocked || optionsBlocked || !orderOptionsComplete() || !approvalValidationPassed;");
        assertThat(html).doesNotContain("addressMissing");
    }

    @Test
    void showsTheOrderOptionsForBothWarehouseAndDropshipDeliveriesInsideTheApproveForm() throws Exception {
        // when
        String html = approval();
        int formStart = html.indexOf("id=\"approval-approve-form\"");
        int buttonsAt = html.indexOf("class=\"buttons mt-5\"");
        int fragmentAt = html.indexOf("fragments/order-options :: orderOptions(${orderOptions}, ${selectedOptions})");

        // then: the fragment sits inside the form, before the buttons, and is not nested inside
        // a th:if="${delivery.dropship}" / "${!delivery.dropship ...}" block (there is none around it)
        assertThat(fragmentAt).isBetween(formStart, buttonsAt);
        assertThat(html).contains("id=\"order-options-blocked\"");
        assertThat(html).contains("deliveries.options.error");
        String script = html.substring(html.indexOf("<script th:inline=\"none\">"), html.indexOf("</script>"));
        assertThat(script).contains("function refreshApprovalSubmitState()");
        assertThat(script).contains("orderOptionsComplete()");
    }

    @Test
    void realisationScreenScrollsTheOptionListToThePreselectedAddress() throws Exception {
        // when
        String html = approval();

        // then
        assertThat(html).contains("addressModalScript");
        assertThat(html).contains("scrollAddressOptionsToSelection();");
    }

    @Test
    void approvalScreenNoLongerCarriesTheRejectForm() throws Exception {
        // when
        String html = approval();

        // then
        assertThat(html).doesNotContain("/reject");
        assertThat(html).doesNotContain("name=\"reason\"");
    }

    @Test
    void guardsAreNeverCombinedWithThReplaceOnTheSameElement() throws Exception {
        // when / then
        assertThat(hasElementWithBothThIfAndThReplace(approval())).isFalse();
    }

    @Test
    void localVariablesAreNeverDeclaredOnTheSameElementThatGuardsOnThem() throws Exception {
        // when / then
        assertThat(hasElementWithBothThIfAndThWith(approval())).isFalse();
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

    private boolean hasElementWithBothThIfAndThWith(String html) {
        Matcher matcher = OPENING_TAG.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            if (tag.contains("th:if") && tag.contains("th:with")) {
                return true;
            }
        }
        return false;
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
    void tellsTheSuperAdminWhenTheMarketplaceChoseTheSupplier() throws Exception {
        // when
        String html = approval();

        // then
        assertThat(html).contains("th:each=\"routed : ${routedOrders}\"");
        assertThat(html).contains("deliveries.approval.routed");
        assertThat(html).contains("deliveries.approval.routed.mismatch");
        assertThat(html).contains("deliveries.approval.routed.unmatched");
    }
}
