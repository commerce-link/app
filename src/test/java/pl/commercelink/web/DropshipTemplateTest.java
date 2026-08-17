package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DropshipTemplateTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/" + template), StandardCharsets.UTF_8);
    }

    @Test
    void confirmationShowsTheConsigneeInsteadOfAnAddressPicker() throws Exception {
        // when
        String html = read("dropshipConfirmation.html");

        // then
        assertThat(html).contains("orders.dropship.confirm.consignee");
        assertThat(html).contains("${consignee.streetAndNumber}");
        assertThat(html).contains("${consignee.phone}");
        assertThat(html).doesNotContain("address-modal");
        assertThat(html).doesNotContain("deliveryAddressId");
    }

    @Test
    void confirmationCarriesTheAllocationsThroughHiddenFields() throws Exception {
        // when
        String html = read("dropshipConfirmation.html");

        // then
        assertThat(html).contains("allocations[__${allocStat.index}__].key.orderId");
        assertThat(html).contains("dropship/confirm");
        assertThat(html).contains("dropship/validate");
        assertThat(html).contains("data-fully-available");
    }

    @Test
    void approvalScreenReplacesTheAddressPanelForDropshipDeliveries() throws Exception {
        // when
        String html = read("deliveryApproval.html");

        // then
        assertThat(html).contains("th:if=\"${delivery.dropship}\"");
        assertThat(html).contains("${!delivery.dropship and suggestedAddress != null}");
        assertThat(html).contains("deliveries.dropship.badge");
    }

    @Test
    void deliveriesPlanningOffersTheDropshipEntryPerDirectToConsumerOrder() throws Exception {
        // when
        String html = read("deliveriesPreview.html");

        // then
        assertThat(html).contains("${dropshipCandidates}");
        assertThat(html).contains("deliveries.preview.dropship.send");
        assertThat(html).contains("deliveries.preview.dropship.multiSupplier");
        assertThat(html).contains("dropshipAvailability.get(candidate.provider)");
        assertThat(html).contains("/dropship");
    }

    @Test
    void orderDetailsNoLongerCarriesTheDropshipAction() throws Exception {
        // when
        String html = read("orderDetails.html");

        // then
        assertThat(html).doesNotContain("order.action.dropship");
        assertThat(html).doesNotContain("dropshipProvider");
    }

    @Test
    void deliveryScreensCarryTheDropshipBadge() throws Exception {
        // when / then
        assertThat(read("deliveryDetails.html")).contains("deliveries.dropship.badge");
        assertThat(read("deliveryDetails.html")).contains("${delivery.dropshipOrderId}");
        assertThat(read("deliveries.html")).contains("deliveries.dropship.badge");
    }
}
