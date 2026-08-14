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
    private static final Pattern OPENING_TAG = Pattern.compile("<[a-zA-Z0-9:]+\\s[^>]*?>", Pattern.DOTALL);

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
    void guardsAreNeverCombinedWithThReplaceOnTheSameElement() throws Exception {
        // then
        assertThat(hasElementWithBothThIfAndThReplace(approval())).isFalse();
        assertThat(hasElementWithBothThIfAndThReplace(details())).isFalse();
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
}
