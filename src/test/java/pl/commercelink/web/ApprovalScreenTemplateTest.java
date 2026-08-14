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
    private static final Path DETAILS = Path.of("src/main/resources/templates/deliveryDetails.html");
    private static final Pattern OPENING_TAG =
            Pattern.compile("<[a-zA-Z0-9:]+(?:\\s+[a-zA-Z0-9:_.-]+(?:=\"[^\"]*\")?)*\\s*/?>", Pattern.DOTALL);

    private String approval() throws Exception {
        return Files.readString(APPROVAL, StandardCharsets.UTF_8);
    }

    private String details() throws Exception {
        return Files.readString(DETAILS, StandardCharsets.UTF_8);
    }

    @Test
    void offersBothApprovalOutcomesOnOneScreen() throws Exception {
        // when
        String html = approval();

        // then
        assertThat(html).contains("deliveries.approval.realize");
        assertThat(html).contains("deliveries.approval.reject");
        assertThat(html).contains("name=\"reason\"");
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
        assertThat(html).contains("approve.disabled = addressBlocked || !approvalValidationPassed;");
        assertThat(html).doesNotContain("addressMissing");
    }

    @Test
    void detailsPageNoLongerCarriesTheApprovalPanel() throws Exception {
        // when
        String html = details();

        // then
        assertThat(html).doesNotContain("approval-approve-button");
        assertThat(html).doesNotContain("approval-validation-area");
        assertThat(html).doesNotContain("pickerScript('deliveryAddressId'");
    }

    @Test
    void detailsPageLinksToTheRealisationScreenNextToSave() throws Exception {
        // when
        String html = details();

        // then
        assertThat(html).contains("deliveries.approval.realize");
        assertThat(html).contains("/approval(storeId=");
    }

    @Test
    void detailsPageShowsApprovalStateAsAStatusTagLikeEveryOtherOrderState() throws Exception {
        // when
        String html = details();
        int statuses = html.indexOf("#{deliveries.statuses}");
        int editFormEnd = html.indexOf("</form>");

        // then
        assertThat(html.indexOf("deliveries.status.awaitingApproval")).isBetween(statuses, editFormEnd);
        assertThat(html.indexOf("deliveries.status.rejected")).isBetween(statuses, editFormEnd);
    }

    @Test
    void detailsPageShowsTheFailureReasonAsALabelledFieldLikeEveryOtherField() throws Exception {
        // when
        String html = details();
        int editFormEnd = html.indexOf("</form>");

        // then
        assertThat(html.indexOf("#{general.reason}")).isBetween(0, editFormEnd);
        assertThat(html.indexOf("delivery.rejectionReason")).isBetween(0, editFormEnd);
        assertThat(html.indexOf("delivery.orderErrorMessage")).isBetween(0, editFormEnd);
        assertThat(html).doesNotContain("notification is-danger is-light");
        assertThat(html).doesNotContain("notification is-warning is-light");
    }

    @Test
    void detailsPageLetsTheStatusTagSpeakForItselfWithoutAProseRestatement() throws Exception {
        // when
        String html = details();

        // then
        assertThat(html).doesNotContain("deliveries.purchase.submitted.approval");
        assertThat(html).doesNotContain("deliveries.approval.rejectedBy");
    }

    @Test
    void guardsAreNeverCombinedWithThReplaceOnTheSameElement() throws Exception {
        // then
        assertThat(hasElementWithBothThIfAndThReplace(approval())).isFalse();
        assertThat(hasElementWithBothThIfAndThReplace(details())).isFalse();
    }

    @Test
    void localVariablesAreNeverDeclaredOnTheSameElementThatGuardsOnThem() throws Exception {
        // then
        assertThat(hasElementWithBothThIfAndThWith(approval())).isFalse();
        assertThat(hasElementWithBothThIfAndThWith(details())).isFalse();
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
}
