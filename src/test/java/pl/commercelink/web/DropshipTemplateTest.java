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
        assertThat(html).contains("${candidate.allocations}");
        assertThat(html).contains("deliveries.allocations");
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
        assertThat(html).contains("!delivery.orderPending and !delivery.dropship}\" value=\"splitSelectedAllocations\"");
    }

    @Test
    void deliveryDetailsOffersDropshipShipmentConfirmationInsteadOfWarehouseReceipt() throws Exception {
        // when
        String html = read("deliveryDetails.html");

        // then
        assertThat(html).contains("value=\"confirmDropshipShipment\"");
        assertThat(html).contains("deliveries.dropship.confirmShipment");
        assertThat(html).contains("${delivery.dropship and delivery.orderStatus == null and (isAdmin or isSuperAdmin)}");
        assertThat(html).contains("id=\"confirmShipmentModal\"");
        assertThat(html).contains("id=\"dsConfirmButton\"");
        assertThat(html).contains("function updateDropshipShipmentButton()");
        assertThat(html).contains("*{shipmentTrackingNo}");
        assertThat(html).contains("*{shipmentShippedAt}");
        assertThat(html).contains("deliveries.dropship.confirm.deleteAllocation");
        assertThat(html).doesNotContain("deliveries.dropship.confirmDelivered");
        assertThat(html).doesNotContain("deliveries.dropship.confirm.delivered");
        assertThat(html).doesNotContain("PersonalCollection");
    }

    @Test
    void deliveryDetailsShowSupplierTrackingRowWithManualCheck() throws Exception {
        // when
        String html = read("deliveryDetails.html");

        // then
        assertThat(html).contains("deliveries.dropship.tracking.label");
        assertThat(html).contains("#{${'deliveries.dropship.tracking.state.' + delivery.effectiveTrackingState.name()}}");
        assertThat(html).contains("deliveries.dropship.tracking.lastChecked");
        assertThat(html).contains("form=\"tracking-check-form\"");
        assertThat(html).contains("id=\"tracking-check-form\"");
        assertThat(html).contains("/tracking/check");
        assertThat(html).contains("deliveries.dropship.tracking.check");
        assertThat(html).contains("fa-truck");
    }

    @Test
    void deliveryDetailsWarnAboutTerminalTrackingStates() throws Exception {
        // when
        String html = read("deliveryDetails.html");

        // then
        assertThat(html).contains("deliveries.dropship.tracking.notice.cancelled");
        assertThat(html).contains("deliveries.dropship.tracking.notice.noData");
        assertThat(html).contains("deliveries.dropship.tracking.notice.givenUp");
        assertThat(html).contains("'CANCELLED_BY_SUPPLIER'");
        assertThat(html).contains("'SHIPPED_WITHOUT_DATA'");
        assertThat(html).contains("'GIVEN_UP'");
    }

    @Test
    void trackingMessagesExistInBothLanguages() throws Exception {
        // given
        String pl = Files.readString(Path.of("src/main/resources/messages_pl.properties"), StandardCharsets.UTF_8);
        String en = Files.readString(Path.of("src/main/resources/messages_en.properties"), StandardCharsets.UTF_8);

        // then
        for (String key : List.of(
                "deliveries.dropship.tracking.label", "deliveries.dropship.tracking.lastChecked",
                "deliveries.dropship.tracking.check",
                "deliveries.dropship.tracking.state.PENDING", "deliveries.dropship.tracking.state.COMPLETED",
                "deliveries.dropship.tracking.state.UNSUPPORTED", "deliveries.dropship.tracking.state.SHIPPED_WITHOUT_DATA",
                "deliveries.dropship.tracking.state.CANCELLED_BY_SUPPLIER", "deliveries.dropship.tracking.state.GIVEN_UP",
                "deliveries.dropship.tracking.notice.cancelled", "deliveries.dropship.tracking.notice.noData",
                "deliveries.dropship.tracking.notice.givenUp",
                "deliveries.dropship.tracking.result.confirmed", "deliveries.dropship.tracking.result.stillProcessing",
                "deliveries.dropship.tracking.result.cancelled", "deliveries.dropship.tracking.result.noData",
                "deliveries.dropship.tracking.result.unavailable")) {
            assertThat(pl).as(key + " in pl").contains("\n" + key + "=");
            assertThat(en).as(key + " in en").contains("\n" + key + "=");
        }
    }
}
