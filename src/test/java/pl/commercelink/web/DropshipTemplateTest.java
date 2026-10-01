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
        assertThat(read("deliveries.html")).contains("deliveries.dropship.badge");
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
    void trackingMessagesExistInBothLanguages() throws Exception {
        // given
        String pl = Files.readString(Path.of("src/main/resources/messages_pl.properties"), StandardCharsets.UTF_8);
        String en = Files.readString(Path.of("src/main/resources/messages_en.properties"), StandardCharsets.UTF_8);

        // when / then
        for (String key : List.of(
                "deliveries.dropship.tracking.state.PENDING", "deliveries.dropship.tracking.state.COMPLETED",
                "deliveries.dropship.tracking.state.UNSUPPORTED", "deliveries.dropship.tracking.state.SHIPPED_WITHOUT_DATA",
                "deliveries.dropship.tracking.state.CANCELLED_BY_SUPPLIER", "deliveries.dropship.tracking.state.GIVEN_UP",
                "deliveries.dropship.tracking.result.confirmed", "deliveries.dropship.tracking.result.stillProcessing",
                "deliveries.dropship.tracking.result.cancelled", "deliveries.dropship.tracking.result.noData",
                "deliveries.dropship.tracking.result.unavailable")) {
            assertThat(pl).as(key + " in pl").contains("\n" + key + "=");
            assertThat(en).as(key + " in en").contains("\n" + key + "=");
        }
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
