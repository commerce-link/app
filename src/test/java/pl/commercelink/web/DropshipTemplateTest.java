package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        assertThat(html).contains("dropship/purchase/back");
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
        assertThat(html).contains("deliveries.dropship.badge");
        assertThat(html).contains("deliveries.preview.create");
        assertThat(html).contains("/dropship");
        assertThat(html).doesNotContain("deliveries.preview.dropship.order");
    }

    @Test
    void createScreenMirrorsTheWarehouseCreateScreen() throws Exception {
        // when
        String dropship = read("dropshipCreate.html");
        String warehouse = read("deliveryCreate.html");

        // then
        assertThat(dropship).contains("deliveries.create.title");
        assertThat(dropship).doesNotContain("orders.dropship.confirm.consignee");
        assertThat(dropship).doesNotContain("${consignee");
        assertThat(dropship).doesNotContain("deliveries.preview.dropship.order");
        assertThat(dropship).contains("dropship/create");
        assertThat(dropship).contains("dropship/purchase");
        assertThat(dropship).contains("deliveries.purchase.button");
        assertThat(dropship).contains("general.save");
        assertThat(dropship).contains("allocations[__${allocStat.index}__].key.orderId");
        for (String sharedField : List.of("*{sourceCurrency}", "*{shippingCost}", "*{paymentCost}",
                "*{paymentTerms}", "*{tax}", "*{removeUnselected}", "*{externalDeliveryId}",
                "*{estimatedDeliveryAt}", "deliveries.create.include", "deliveries.create.netValue")) {
            assertThat(dropship).contains(sharedField);
            assertThat(warehouse).contains(sharedField);
        }
    }

    @Test
    void createScreenDoesNotLetTheOrderedQuantityGrow() throws Exception {
        // when
        String html = read("dropshipCreate.html");

        // then
        assertThat(html).doesNotContain("deliveries.create.qtyIncrease.note");
        assertThat(html).doesNotContain("warehouseAdjustment");
        assertThat(html).doesNotContain("deliveries.minQty");
        assertThat(html).contains("type=\"hidden\" th:field=\"*{items[__${itemStat.index}__].requestedQty}\"");
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
        assertThat(read("deliveryDetails.html")).doesNotContain("deliveries.dropship.orderLink");
        assertThat(read("deliveries.html")).contains("deliveries.dropship.badge");
    }

    @Test
    void deliveryDetailsShowTheDropshipContactBelowTheAddress() throws Exception {
        // when
        String html = read("deliveryDetails.html");

        // then
        assertThat(html).contains("${dropshipContact.phone}");
        assertThat(html).contains("${dropshipContact.email}");
        assertThat(html.indexOf("${dropshipContact.phone}")).isGreaterThan(html.indexOf("deliveries.deliveryAddress"));
        assertThat(html).contains("order.shipment.type");
        assertThat(html).contains("${dropshipShipment.type.name()}");
        assertThat(html).contains("${dropshipShipment.collectionPointCode}");
    }

    @Test
    void deliveryDetailsHideMergeAndSplitForDropshipDeliveries() throws Exception {
        // when
        String html = read("deliveryDetails.html");

        // then
        assertThat(html).contains("!delivery.dropship and !mergeTargetDeliveries.isEmpty()");
        assertThat(html).contains("(isAdmin or isSuperAdmin) and !delivery.dropship}\" value=\"splitSelectedAllocations\"");
    }
}
