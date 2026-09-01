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
        assertThat(html).contains("fragments/consignee-address :: consigneeAddress(${consignee}, ${pickupShipment})");
        assertThat(html).doesNotContain("${consignee.streetAndNumber}");
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
    void deliveriesListKeepsStatusTagsInTheirOwnColumn() throws Exception {
        // when
        String html = read("deliveries.html");

        // then
        assertThat(html).contains("<th th:text=\"#{deliveries.list.status}\"></th>");
        int deliveryIdCell = html.indexOf("${delivery.shortenedDeliveryId}");
        int cellEnd = html.indexOf("</td>", deliveryIdCell);
        assertThat(html.substring(deliveryIdCell, cellEnd)).doesNotContain("class=\"tag");
    }

    @Test
    void deliveriesListShowsTheDeliveryTypeInItsOwnColumn() throws Exception {
        // when
        String html = read("deliveries.html");

        // then - a "Typ" column right after the supplier, before the delivery number
        int typeHeader = html.indexOf("<th th:text=\"#{deliveries.list.type}\"></th>");
        assertThat(typeHeader).isGreaterThan(html.indexOf("<th th:text=\"#{deliveries.provider}\"></th>"));
        assertThat(typeHeader).isLessThan(html.indexOf("<th th:text=\"#{deliveries.order.no}\"></th>"));
        // the type cell names both kinds, the status cell no longer carries the dropship badge
        int typeCell = html.lastIndexOf("<td>", html.indexOf("deliveries.dropship.badge"));
        int typeCellEnd = html.indexOf("</td>", typeCell);
        String typeCellHtml = html.substring(typeCell, typeCellEnd);
        assertThat(typeCellHtml).contains("deliveries.type.warehouse");
        // both kinds are tags, in different colours
        assertThat(typeCellHtml).contains("<span class=\"tag is-info is-light mb-0\" th:if=\"${delivery.dropship}\"");
        assertThat(typeCellHtml).contains("<span class=\"tag is-primary is-light mb-0\" th:unless=\"${delivery.dropship}\"");
        int statusCell = html.indexOf("deliveries.status.orderPending");
        int statusCellEnd = html.indexOf("</td>", statusCell);
        assertThat(html.substring(statusCell, statusCellEnd)).doesNotContain("deliveries.dropship.badge");
        assertThat(typeCell).isLessThan(statusCell);
        for (String key : List.of("deliveries.list.type", "deliveries.type.warehouse")) {
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
    void orderDetailsGreysOutWarehouseMovesWhenItemsSitInADropshipDelivery() throws Exception {
        // when
        String html = read("orderDetails.html");
        String pl = Files.readString(Path.of("src/main/resources/messages_pl.properties"), StandardCharsets.UTF_8);
        String en = Files.readString(Path.of("src/main/resources/messages_en.properties"), StandardCharsets.UTF_8);

        // then
        assertThat(html).contains("value=\"moveSelectedItemsToTheWarehouse\" th:disabled=\"${hasDropshipItems}\"");
        assertThat(html).contains("value=\"moveSelectedItemsToTheWarehouseForRMA\" th:disabled=\"${hasDropshipItems}\"");
        assertThat(html).contains("order.items.action.move.warehouse.dropship");
        assertThat(pl).contains("order.items.action.move.warehouse.dropship=");
        assertThat(pl).contains("order.items.action.move.warehouse.dropship.error=");
        assertThat(en).contains("order.items.action.move.warehouse.dropship=");
        assertThat(en).contains("order.items.action.move.warehouse.dropship.error=");
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
    void createScreenCarriesTheDropshipBadgeAndTheConsignee() throws Exception {
        // when
        String html = read("dropshipCreate.html");

        // then
        assertThat(html).contains("<span class=\"tag is-info ml-2\" th:text=\"#{deliveries.dropship.badge}\">");
        assertThat(html).contains("fragments/consignee-address :: consigneeAddress(${consignee}, ${pickupShipment})");
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
    void createScreenBlocksTheSupplierOrderButtonWithAReasonAndShowsThePickupPoint() throws Exception {
        // given
        String create = read("dropshipCreate.html");
        String fragment = read("fragments/consignee-address.html");

        // then
        assertThat(create).contains("th:disabled=\"${purchaseBlockedReason != null}\"");
        assertThat(create).contains("#{${purchaseBlockedReason}}");
        assertThat(create).contains("consigneeAddress(${consignee}, ${pickupShipment})");
        assertThat(fragment).contains("th:fragment=\"consigneeAddress(consignee, pickupShipment)\"");
        assertThat(fragment).contains("#{orders.dropship.confirm.pickupPoint}");
        assertThat(fragment).contains("${pickupShipment.collectionPointCode}");
        assertThat(read("dropshipConfirmation.html")).contains("consigneeAddress(${consignee}, ${pickupShipment})");
        assertThat(read("deliveryApproval.html")).contains("consigneeAddress(${consignee}, ${pickupShipment})");
    }

    @Test
    void confirmationScreenShowsThePurchaseBlockedReasonAndDisablesTheSubmitButton() throws Exception {
        // when
        String html = read("dropshipConfirmation.html");

        // then
        assertThat(html).contains("th:if=\"${purchaseBlockedReason != null}\"");
        assertThat(html).contains("#{${purchaseBlockedReason}}");
        assertThat(html).contains("id=\"purchase-confirm-submit\"");
        assertThat(html).contains("th:disabled=\"${purchaseBlockedReason != null}\"");
        assertThat(html).contains("th:attr=\"data-blocked=${purchaseBlockedReason != null}\"");
        assertThat(html).contains("submitButton.dataset.blocked === 'true'");
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
    void dropshipConfirmationRendersOrderOptionsInsideTheFormAndGatesSubmitOnThem() throws Exception {
        // when
        String html = read("dropshipConfirmation.html");

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

    @Test
    void orderDetailsHidesTheGoodsIssueActionForOrdersWithoutWarehouseItems() throws Exception {
        // when
        String html = read("orderDetails.html");

        // then
        // th:if on the goods-issue form: only rendered when there are warehouse items to issue
        assertThat(html).contains("${hasWarehouseDocumentsEnabled and hasWarehouseItems and !hasWarehouseDocument}");
        // th:disabled on the dropdown trigger: the same clause, negated, inside the compound condition
        assertThat(html).contains("!(hasWarehouseDocumentsEnabled and hasWarehouseItems and !hasWarehouseDocument))}");
    }
}
