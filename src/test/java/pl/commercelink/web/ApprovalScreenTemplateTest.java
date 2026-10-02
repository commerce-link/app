package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalScreenTemplateTest {

    private static final Path APPROVAL = Path.of("src/main/resources/templates/deliveries/approval.html");
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
    void detailsPageHidesTheRetryButtonOnceTheDeliveryHasBeenReceived() throws Exception {
        // when
        String html = details();
        String retryFormTag = openingTagOf(html, "purchase/retry");

        // then
        assertThat(retryFormTag).contains("!delivery.hasBeenReceived()");
        assertThat(retryFormTag).contains("delivery.documents.isEmpty()");
    }

    @Test
    void detailsPageOffersARejectModalPostingToTheRejectRoute() throws Exception {
        // when
        String html = details();

        // then
        assertThat(html).contains("id=\"rejectPurchaseModal\"");
        assertThat(html).contains("/dashboard/store/${delivery.storeId}/deliveries/${delivery.deliveryId}/reject");
        assertThat(html).contains("<textarea class=\"textarea\" name=\"reason\">");
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
