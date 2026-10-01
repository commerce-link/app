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
    void approvalScreenReplacesTheAddressPanelForDropshipDeliveries() throws Exception {
        // when
        String html = read("deliveryApproval.html");

        // then
        assertThat(html).contains("th:if=\"${delivery.dropship}\"");
        assertThat(html).contains("${!delivery.dropship and suggestedAddress != null}");
        assertThat(html).contains("deliveries.dropship.badge");
    }

    @Test
    void deliveryScreensCarryTheDropshipBadge() throws Exception {
        // when / then
        assertThat(read("deliveryDetails.html")).contains("deliveries.dropship.badge");
        assertThat(read("deliveryDetails.html")).doesNotContain("deliveries.dropship.orderLink");
        assertThat(read("deliveries.html")).contains("deliveries.dropship.badge");
    }

    @Test
    void deliveryDetailsShowTheDeliveryTypeNextToTheOrderNumberNotAmongStatuses() throws Exception {
        // when
        String html = read("deliveryDetails.html");

        // then - both kinds get a tag right after the delivery number, same colours as the list
        int numberField = html.indexOf("<label class=\"label\" th:text=\"#{deliveries.order.no}\"></label>");
        int numberFieldEnd = html.indexOf("<label class=\"label\" th:text=\"#{general.provider.name}\"></label>", numberField);
        String numberFieldHtml = html.substring(numberField, numberFieldEnd);
        assertThat(numberFieldHtml).contains("<span class=\"tag is-info is-light ml-2\" th:if=\"${delivery.dropship}\" th:text=\"#{deliveries.dropship.badge}\">");
        assertThat(numberFieldHtml).contains("<span class=\"tag is-primary is-light ml-2\" th:unless=\"${delivery.dropship}\" th:text=\"#{deliveries.type.warehouse}\">");
        // and the statuses block no longer carries the dropship badge
        int statuses = html.indexOf("deliveries.status.paid");
        int statusesEnd = html.indexOf("deliveries.dropship.tracking.state.", statuses);
        assertThat(html.substring(statuses, statusesEnd)).doesNotContain("deliveries.dropship.badge");
    }

    @Test
    void deliveriesListShowsTheDeliveryTypeUnderTheNumberAndStatusInItsOwnColumn() throws Exception {
        // when
        String html = read("deliveries.html");

        // then - the type is the second line of the number cell, both kinds named, neither is a status tag
        int numberCell = html.indexOf("${row.number()}");
        int numberCellEnd = html.indexOf("</th>", numberCell);
        String numberCellHtml = html.substring(numberCell, numberCellEnd);
        assertThat(numberCellHtml).contains("cl-icon-text").contains("deliveries.dropship.badge").contains("deliveries.type.warehouse");
        assertThat(numberCellHtml).doesNotContain("cl-status");
        // and the status cell carries the state, never the dropship badge
        int statusCell = html.indexOf("${row.stateLabel()}");
        int statusCellEnd = html.indexOf("</td>", statusCell);
        assertThat(html.substring(statusCell, statusCellEnd)).doesNotContain("deliveries.dropship.badge");
        assertThat(numberCell).isLessThan(statusCell);
        for (String key : List.of("deliveries.type.warehouse", "deliveries.dropship.badge")) {
            assertThat(Files.readString(Path.of("src/main/resources/messages_pl.properties"), StandardCharsets.UTF_8)).contains("\n" + key + "=");
            assertThat(Files.readString(Path.of("src/main/resources/messages_en.properties"), StandardCharsets.UTF_8)).contains("\n" + key + "=");
        }
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
    void deliveryDetailsGreyOutWarehouseActionsForDropshipDeliveries() throws Exception {
        // when
        String html = read("deliveryDetails.html");
        String pl = Files.readString(Path.of("src/main/resources/messages_pl.properties"), StandardCharsets.UTF_8);
        String en = Files.readString(Path.of("src/main/resources/messages_en.properties"), StandardCharsets.UTF_8);

        // then: reception, merge and split stay visible but disabled, with a tooltip explaining why
        assertThat(html).contains("th:unless=\"${delivery.awaitingApproval or isSuperAdmin}\" th:disabled=\"${delivery.dropship}\" th:title=\"${delivery.dropship} ? #{deliveries.action.dropship.disabled} : ''\" value=\"markSelectedAsReceived\"");
        assertThat(html).contains("!mergeTargetDeliveries.isEmpty() and !delivery.orderDispatched}\" th:disabled=\"${delivery.dropship}\" th:title=\"${delivery.dropship} ? #{deliveries.action.dropship.disabled} : ''\" value=\"mergeSelectedAllocations\"");
        assertThat(html).contains("!delivery.orderPending and !delivery.orderDispatched}\" th:disabled=\"${delivery.dropship}\" th:title=\"${delivery.dropship} ? #{deliveries.action.dropship.disabled} : ''\" value=\"splitSelectedAllocations\"");
        assertThat(html).doesNotContain("!delivery.dropship and !mergeTargetDeliveries.isEmpty()");
        assertThat(pl).contains("deliveries.action.dropship.disabled=");
        assertThat(en).contains("deliveries.action.dropship.disabled=");
    }

    @Test
    void deliveryDetailsOffersAllocationRemovalWhenTheSupplierOrderOutcomeIsUnknown() throws Exception {
        // when
        String template = Files.readString(Path.of("src/main/resources/templates/deliveryDetails.html"));

        // then
        assertThat(template).contains(
                "!delivery.orderPending and (!delivery.orderDispatched or delivery.orderOutcomeUnknown)}\" value=\"deleteSelectedAllocations\"");
        assertThat(template).contains("deliveries.unknownOutcome.confirm.deleteAllocation");
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
    void deliveryDetailsShowSupplierTrackingTagAmongStatusesWithoutAManualCheckButton() throws Exception {
        // when
        String html = read("deliveryDetails.html");

        // then - the tag alone; checks run on the tracking cron, the manual button was dropped
        assertThat(html).doesNotContain("deliveries.dropship.tracking.label");
        assertThat(html).doesNotContain("deliveries.dropship.tracking.lastChecked");
        assertThat(html).contains("#{${'deliveries.dropship.tracking.state.' + trackingState}}");
        assertThat(html).doesNotContain("tracking-check-form");
        assertThat(html).doesNotContain("/tracking/check");
        assertThat(html).doesNotContain("deliveries.dropship.tracking.check\"");
        assertThat(html).doesNotContain("fa-truck");
    }

    @Test
    void trackingStateLabelsStartWithACapitalLetter() throws Exception {
        for (String file : List.of("messages_pl.properties", "messages_en.properties")) {
            String messages = Files.readString(Path.of("src/main/resources/" + file), StandardCharsets.UTF_8);
            for (String line : messages.split("\n")) {
                if (line.startsWith("deliveries.dropship.tracking.state.")) {
                    String label = line.substring(line.indexOf('=') + 1);
                    assertThat(Character.isUpperCase(label.charAt(0))).as(file + ": " + line).isTrue();
                }
            }
        }
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

    @Test
    void deliveryDetailsHideTheOrderedQuantityPencilForDropshipDeliveries() throws Exception {
        // when
        String html = read("deliveryDetails.html");
        String pl = Files.readString(Path.of("src/main/resources/messages_pl.properties"), StandardCharsets.UTF_8);
        String en = Files.readString(Path.of("src/main/resources/messages_en.properties"), StandardCharsets.UTF_8);

        // then
        assertThat(html).contains("itemQtyEditable=${(isAdmin or isSuperAdmin) and !delivery.dropship and");
        assertThat(pl).contains("deliveries.editOrderedQty.error.dropship=");
        assertThat(en).contains("deliveries.editOrderedQty.error.dropship=");
    }

    @Test
    void consigneeAddressIsOneSharedFragment() throws Exception {
        // when
        String fragment = read("fragments/consignee-address.html");

        // then
        assertThat(fragment).contains("th:fragment=\"consigneeAddress(consignee, pickupShipment)\"");
        assertThat(fragment).contains("orders.dropship.consignee.address");
        for (String field : List.of("${consignee.displayName}", "${consignee.streetAndNumber}", "${consignee.postalCode}",
                "${consignee.city}", "${consignee.country}", "${consignee.phone}", "${consignee.email}")) {
            assertThat(fragment).contains(field);
        }
    }

    @Test
    void approvalScreenShowsTheConsigneeOfADropshipDelivery() throws Exception {
        // when
        String html = read("deliveryApproval.html");

        // then
        assertThat(html).contains("th:if=\"${delivery.dropship and consignee != null}\"");
        assertThat(html).contains("fragments/consignee-address :: consigneeAddress(${consignee}, ${pickupShipment})");
    }

    @Test
    void consigneeFragmentsShowThePickupPointAndTheApprovalScreenKeepsTheBoxVariant() throws Exception {
        // given
        String fragment = read("fragments/consignee-address.html");

        // then
        assertThat(fragment).contains("th:fragment=\"consigneeAddress(consignee, pickupShipment)\"");
        assertThat(fragment).contains("th:fragment=\"clConsignee(consignee, pickupShipment)\"");
        assertThat(fragment).contains("#{orders.dropship.confirm.pickupPoint}");
        assertThat(fragment).contains("${pickupShipment.collectionPointCode}");
        assertThat(read("deliveryApproval.html")).contains("consigneeAddress(${consignee}, ${pickupShipment})");
    }

    @Test
    void pickupPointMessagesExistInBothLanguages() throws Exception {
        // given
        String pl = Files.readString(Path.of("src/main/resources/messages_pl.properties"), StandardCharsets.UTF_8);
        String en = Files.readString(Path.of("src/main/resources/messages_en.properties"), StandardCharsets.UTF_8);

        // then
        for (String key : List.of("orders.dropship.error.pickupPointUnsupported",
                "orders.dropship.error.pickupPointIncomplete", "orders.dropship.confirm.pickupPoint")) {
            assertThat(pl).as(key + " in pl").contains("\n" + key + "=");
            assertThat(en).as(key + " in en").contains("\n" + key + "=");
        }
    }

    @Test
    void deliveryDetailsWarnsWhenWarehouseGoodsAreBoundForTheCustomer() throws Exception {
        // when
        String html = read("deliveryDetails.html");
        int conditionAt = html.indexOf("th:if=\"${delivery.hasDirectToConsumerAllocations()}\"");
        int noticeAt = html.indexOf("deliveries.directToConsumer.viaWarehouse.notice");

        // then: the message key sits inside the element guarded by that exact condition, not
        // merely somewhere in the file
        assertThat(conditionAt).isGreaterThan(-1);
        assertThat(noticeAt).isBetween(conditionAt, conditionAt + 150);
    }

    @Test
    void deliveryApprovalWarnsWhenWarehouseGoodsAreBoundForTheCustomer() throws Exception {
        // when
        String html = read("deliveryApproval.html");
        int conditionAt = html.indexOf("th:if=\"${delivery.hasDirectToConsumerAllocations()}\"");
        int noticeAt = html.indexOf("deliveries.directToConsumer.viaWarehouse.notice");

        // then: the message key sits inside the element guarded by that exact condition, not
        // merely somewhere in the file
        assertThat(conditionAt).isGreaterThan(-1);
        assertThat(noticeAt).isBetween(conditionAt, conditionAt + 150);
    }
}
